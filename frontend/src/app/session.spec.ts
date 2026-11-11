import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { KEYCLOAK, Session } from './session';

describe('Nomion session', () => {
  const user = {
    id: 'local-user',
    name: 'Usuária Teste',
    email: 'teste@example.test',
    memberships: [
      { tenantId: 'alpha', tenantName: 'Alfa' },
      { tenantId: 'beta', tenantName: 'Beta' },
    ],
  };
  const adapter = {
    authenticated: true,
    token: 'access-token',
    init: vi.fn().mockResolvedValue(true),
    updateToken: vi.fn().mockResolvedValue(false),
    login: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
    clearToken: vi.fn(),
  };
  let session: Session;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: KEYCLOAK, useValue: adapter },
      ],
    });
    session = TestBed.inject(Session);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('initializes with PKCE and lists offices without a tenant header', async () => {
    const initialized = session.initialize();
    await Promise.resolve();
    await Promise.resolve();
    const request = http.expectOne('/api/me');
    expect(adapter.init).toHaveBeenCalledWith(
      expect.objectContaining({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
      }),
    );
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token');
    expect(request.request.headers.has('X-Tenant-Id')).toBe(false);
    request.flush({ user, activeTenantId: null, roles: [], permissions: [] });
    await initialized;
    expect(session.account()?.memberships).toEqual(user.memberships);
  });

  it('clears the previous context immediately and discards a late response', async () => {
    const initial = session.loadProfile('alpha');
    await Promise.resolve();
    http.expectOne('/api/me').flush({
      user,
      activeTenantId: 'alpha',
      roles: ['TENANT_ADMIN'],
      permissions: ['CONTRACT_CREATE'],
    });
    await initial;
    expect(session.context()?.permissions).toEqual(['CONTRACT_CREATE']);

    const older = session.loadProfile('alpha');
    await Promise.resolve();
    const previous = http.expectOne('/api/me');
    const switching = session.loadProfile('beta');
    expect(session.context()).toBeNull();
    await Promise.resolve();
    const next = http.expectOne('/api/me');
    expect(next.request.headers.get('X-Tenant-Id')).toBe('beta');
    next.flush({ user, activeTenantId: 'beta', roles: ['VIEWER'], permissions: ['CONTRACT_READ'] });
    await switching;
    previous.flush({
      user,
      activeTenantId: 'alpha',
      roles: ['TENANT_ADMIN'],
      permissions: ['CONTRACT_CREATE'],
    });
    await older;
    expect(session.context()?.activeTenantId).toBe('beta');
    expect(session.context()?.permissions).toEqual(['CONTRACT_READ']);
  });

  it('keeps access cleared when the selected office is refused', async () => {
    const selection = session.loadProfile('beta');
    await Promise.resolve();
    http.expectOne('/api/me').flush({}, { status: 403, statusText: 'Forbidden' });
    await selection;
    expect(session.context()).toBeNull();
    expect(session.error()).toContain('negado');
  });

  it('clears account and context before redirecting for logout', async () => {
    const selection = session.loadProfile('alpha');
    await Promise.resolve();
    http.expectOne('/api/me').flush({ user, activeTenantId: 'alpha', roles: [], permissions: [] });
    await selection;
    await session.logout();
    expect(session.account()).toBeNull();
    expect(session.context()).toBeNull();
    expect(adapter.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin + '/' });
  });

  it('scopes API calls to the selected office and rejects a response after switching', async () => {
    const selection = session.loadProfile('alpha');
    await Promise.resolve();
    http.expectOne('/api/me').flush({ user, activeTenantId: 'alpha', roles: [], permissions: [] });
    await selection;
    const pending = session.request('GET', '/public-entities');
    const rejected = expect(pending).rejects.toThrow('O escritório ativo foi alterado.');
    await Promise.resolve();
    const request = http.expectOne('/api/public-entities');
    expect(request.request.headers.get('X-Tenant-Id')).toBe('alpha');
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token');
    const switching = session.loadProfile('beta');
    await Promise.resolve();
    http.expectOne('/api/me').flush({ user, activeTenantId: 'beta', roles: [], permissions: [] });
    await switching;
    request.flush({ content: [{ name: 'Dados do escritório anterior' }] });
    await rejected;
    expect(session.context()?.activeTenantId).toBe('beta');
  });

  it('removes the active context when an operational request is forbidden', async () => {
    const selection = session.loadProfile('alpha');
    await Promise.resolve();
    http.expectOne('/api/me').flush({ user, activeTenantId: 'alpha', roles: [], permissions: [] });
    await selection;
    const pending = session.request('GET', '/contracts');
    const rejected = expect(pending).rejects.toBeDefined();
    await Promise.resolve();
    http.expectOne('/api/contracts').flush({}, { status: 403, statusText: 'Forbidden' });
    await rejected;
    expect(session.context()).toBeNull();
    expect(session.error()).toContain('acesso foi alterado');
  });

  it('clears the session when token renewal fails before an API call', async () => {
    const selection = session.loadProfile('alpha');
    await Promise.resolve();
    http.expectOne('/api/me').flush({ user, activeTenantId: 'alpha', roles: [], permissions: [] });
    await selection;
    adapter.updateToken.mockRejectedValueOnce(new Error('Refresh failed'));
    await expect(session.request('GET', '/contracts')).rejects.toThrow('renovar sua sessão');
    http.expectNone('/api/contracts');
    expect(session.context()).toBeNull();
    expect(session.account()).toBeNull();
    expect(session.authenticated()).toBe(false);
  });
});

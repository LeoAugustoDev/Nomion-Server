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
    expect(adapter.init).toHaveBeenCalledWith(expect.objectContaining({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
    }));
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
      user, activeTenantId: 'alpha', roles: ['TENANT_ADMIN'], permissions: ['CONTRACT_CREATE'],
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
      user, activeTenantId: 'alpha', roles: ['TENANT_ADMIN'], permissions: ['CONTRACT_CREATE'],
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
});

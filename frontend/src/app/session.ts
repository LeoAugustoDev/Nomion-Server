import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { firstValueFrom } from 'rxjs';

export const KEYCLOAK = new InjectionToken<Keycloak>('Nomion Keycloak');

interface Membership {
  tenantId: string;
  tenantName: string;
}

interface Account {
  id: string;
  name: string;
  email: string;
  memberships: Membership[];
}

interface Profile {
  user: Account;
  activeTenantId: string | null;
  roles: string[];
  permissions: string[];
}

@Injectable({ providedIn: 'root' })
export class Session {
  private readonly keycloak = inject(KEYCLOAK);
  private readonly http = inject(HttpClient);
  private readonly accountState = signal<Account | null>(null);
  private readonly contextState = signal<Profile | null>(null);
  private readonly busyState = signal(true);
  private readonly authenticatedState = signal(false);
  private readonly readyState = signal(false);
  private readonly errorState = signal('');
  private requestVersion = 0;

  readonly account = this.accountState.asReadonly();
  readonly context = this.contextState.asReadonly();
  readonly busy = this.busyState.asReadonly();
  readonly authenticated = this.authenticatedState.asReadonly();
  readonly ready = this.readyState.asReadonly();
  readonly error = this.errorState.asReadonly();

  async initialize(): Promise<void> {
    try {
      const authenticated = await this.keycloak.init({
        onLoad: 'check-sso',
        flow: 'standard',
        pkceMethod: 'S256',
        redirectUri: this.redirectUri,
      });
      this.readyState.set(true);
      this.authenticatedState.set(authenticated);
      if (authenticated) {
        await this.loadProfile();
        return;
      }
    } catch {
      this.errorState.set('Não foi possível conectar ao serviço de login. Recarregue a página.');
    }
    this.busyState.set(false);
  }

  async login(): Promise<void> {
    this.busyState.set(true);
    this.errorState.set('');
    try {
      await this.keycloak.login({ redirectUri: this.redirectUri });
    } catch {
      this.errorState.set('Não foi possível iniciar o login. Tente novamente.');
      this.busyState.set(false);
    }
  }

  async logout(): Promise<void> {
    this.requestVersion++;
    this.accountState.set(null);
    this.contextState.set(null);
    this.authenticatedState.set(false);
    this.busyState.set(true);
    this.errorState.set('');
    try {
      await this.keycloak.logout({ redirectUri: this.redirectUri });
    } catch {
      this.keycloak.clearToken();
      this.errorState.set('O acesso local foi encerrado. O Keycloak não confirmou a saída.');
      this.busyState.set(false);
    }
  }

  async loadProfile(tenantId?: string): Promise<void> {
    const version = ++this.requestVersion;
    this.contextState.set(null);
    this.errorState.set('');
    this.busyState.set(true);
    try {
      await this.keycloak.updateToken(30);
      if (version !== this.requestVersion) {
        return;
      }
      const token = this.keycloak.token;
      if (!token) {
        this.clearSession();
        return;
      }

      let headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
      if (tenantId) {
        headers = headers.set('X-Tenant-Id', tenantId);
      }
      const profile = await firstValueFrom(this.http.get<Profile>('/api/me', { headers }));
      if (version !== this.requestVersion) {
        return;
      }
      if (profile.activeTenantId !== (tenantId ?? null)) {
        throw new Error('Contexto inesperado');
      }

      this.accountState.set(profile.user);
      if (tenantId) {
        this.contextState.set(profile);
      }
    } catch (error) {
      if (version === this.requestVersion) {
        this.reportProfileError(error, tenantId);
      }
    } finally {
      if (version === this.requestVersion) {
        this.busyState.set(false);
      }
    }
  }

  private reportProfileError(error: unknown, tenantId?: string): void {
    if (!this.keycloak.token || !this.keycloak.authenticated) {
      this.clearSession();
    } else if (error instanceof HttpErrorResponse && error.status === 401) {
      this.accountState.set(null);
      this.errorState.set('A API recusou a sessão. Verifique se sua conta está vinculada ao Nomion.');
    } else if (tenantId && error instanceof HttpErrorResponse && error.status === 403) {
      this.errorState.set('Seu acesso a esse escritório foi negado. Atualize a lista e selecione outro.');
    } else {
      this.errorState.set('Não foi possível consultar o Nomion. Tente novamente.');
    }
  }

  private clearSession(): void {
    this.keycloak.clearToken();
    this.accountState.set(null);
    this.contextState.set(null);
    this.authenticatedState.set(false);
    this.errorState.set('Sua sessão expirou. Entre novamente.');
  }

  private get redirectUri(): string {
    return `${window.location.origin}/`;
  }
}

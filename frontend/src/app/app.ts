import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { Session } from './session';
import { Icon } from './icon';
import { Workspace, WorkspacePage } from './workspace';

@Component({
  selector: 'app-root',
  imports: [Icon, Workspace],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  protected readonly session = inject(Session);
  protected readonly page = signal<WorkspacePage>('overview');
  protected readonly menuOpen = signal(false);
  protected readonly selectedOffice = computed(() => {
    const tenantId = this.session.context()?.activeTenantId;
    return this.session.account()?.memberships.find((office) => office.tenantId === tenantId);
  });
  protected readonly role = computed(() => {
    const roles = this.session.context()?.roles ?? [];
    if (roles.includes('PLATFORM_ADMIN')) return 'Administrador da plataforma';
    if (roles.includes('TENANT_ADMIN')) return 'Administrador';
    if (roles.includes('CONSULTANT')) return 'Consultor';
    if (roles.includes('ANALYST')) return 'Analista';
    return 'Visualização';
  });
  protected readonly navigation: { page: WorkspacePage; label: string; icon: string }[] = [
    { page: 'overview', label: 'Visão geral', icon: 'grid' },
    { page: 'entities', label: 'Órgãos públicos', icon: 'building' },
    { page: 'contracts', label: 'Contratos', icon: 'file' },
  ];
  protected readonly upcoming = [
    { label: 'Licitações', icon: 'scale' },
    { label: 'Demandas', icon: 'file' },
    { label: 'Agenda e prazos', icon: 'calendar' },
    { label: 'Tributário', icon: 'building' },
    { label: 'Documentos', icon: 'folder' },
    { label: 'Assistente IA', icon: 'spark' },
  ];

  ngOnInit(): void {
    void this.session.initialize();
  }

  protected initials(name: string): string {
    return name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => word[0])
      .join('')
      .toUpperCase();
  }

  protected async selectOffice(tenantId: string): Promise<void> {
    this.navigate('overview');
    await this.session.loadProfile(tenantId);
  }

  protected navigate(page: WorkspacePage): void {
    this.page.set(page);
    this.menuOpen.set(false);
  }

  protected reload(): void {
    window.location.reload();
  }
}

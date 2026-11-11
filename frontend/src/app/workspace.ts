import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { Session } from './session';
import { Icon } from './icon';
import {
  Contract,
  Page,
  PublicEntity,
  contractStatuses,
  entityTypes,
  errorMessage,
} from './models';

export type WorkspacePage = 'overview' | 'entities' | 'contracts' | 'team' | 'profile';

@Component({
  selector: 'app-workspace',
  imports: [Icon, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './workspace.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Workspace {
  readonly page = input.required<WorkspacePage>();
  readonly navigate = output<WorkspacePage>();
  protected readonly session = inject(Session);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly feedback = signal('');
  protected readonly entities = signal<Page<PublicEntity> | null>(null);
  protected readonly contracts = signal<Page<Contract> | null>(null);
  protected readonly attention = signal<Contract[]>([]);
  protected readonly stats = signal<{
    entities: number | null;
    contracts: number | null;
    active: number | null;
    activeEntities: number | null;
  } | null>(null);
  protected readonly selectedEntity = signal<PublicEntity | null>(null);
  protected readonly selectedContract = signal<Contract | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal('');
  protected readonly resendError = signal('');
  protected readonly today = new Date();
  protected readonly types = entityTypes;
  protected readonly statuses = contractStatuses;
  protected readonly typeOptions = Object.entries(entityTypes);
  protected readonly statusOptions = Object.entries(contractStatuses);
  protected readonly title = {
    overview: 'Visão geral',
    entities: 'Órgãos públicos',
    contracts: 'Contratos',
    team: 'Equipe',
    profile: 'Meu perfil',
  };
  protected readonly description = {
    overview: 'Acompanhe as informações do seu escritório.',
    entities: 'Entes e entidades atendidos pelo escritório.',
    contracts: 'Vigência, valores e responsáveis dos contratos administrativos.',
    team: 'Convide profissionais para trabalhar neste escritório.',
    profile: 'Seus dados e permissões no ambiente selecionado.',
  };
  private listVersion = 0;
  private filters: Record<string, string> = {};
  private listPage = 0;
  private invitationKey: string | null = null;

  @ViewChild('entityDialog', { static: true }) private entityDialog!: ElementRef<HTMLDialogElement>;
  @ViewChild('contractDialog', { static: true })
  private contractDialog!: ElementRef<HTMLDialogElement>;

  constructor() {
    effect(() => {
      this.page();
      this.filters = {};
      this.listPage = 0;
      this.feedback.set('');
      this.formError.set('');
      this.resendError.set('');
      this.invitationKey = null;
      void this.load();
    });
  }

  protected can(permission: string): boolean {
    return this.session.context()?.permissions.includes(permission) ?? false;
  }

  protected async load(): Promise<void> {
    const version = ++this.listVersion;
    const page = this.page();
    this.error.set('');
    this.entities.set(null);
    this.contracts.set(null);
    this.attention.set([]);
    this.stats.set(null);
    if (page === 'profile' || page === 'team') {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    try {
      if (page === 'overview') {
        const orgs = this.can('PUBLIC_ENTITY_READ');
        const contracts = this.can('CONTRACT_READ');
        const until = new Date();
        until.setDate(until.getDate() + 60);
        const endDateTo = [
          until.getFullYear(),
          String(until.getMonth() + 1).padStart(2, '0'),
          String(until.getDate()).padStart(2, '0'),
        ].join('-');
        const [entityList, contractList, active, activeEntities, attention] = await Promise.all([
          orgs
            ? this.session.request<Page<PublicEntity>>('GET', '/public-entities', undefined, {
                size: '1',
              })
            : null,
          contracts
            ? this.session.request<Page<Contract>>('GET', '/contracts', undefined, { size: '1' })
            : null,
          contracts
            ? this.session.request<Page<Contract>>('GET', '/contracts', undefined, {
                status: 'ACTIVE',
                size: '1',
              })
            : null,
          orgs
            ? this.session.request<Page<PublicEntity>>('GET', '/public-entities', undefined, {
                active: 'true',
                size: '1',
              })
            : null,
          contracts
            ? this.session.request<Page<Contract>>('GET', '/contracts', undefined, {
                status: 'ACTIVE',
                endDateTo,
                size: '5',
              })
            : null,
        ]);
        if (version !== this.listVersion) return;
        this.stats.set({
          entities: entityList?.totalElements ?? null,
          contracts: contractList?.totalElements ?? null,
          active: active?.totalElements ?? null,
          activeEntities: activeEntities?.totalElements ?? null,
        });
        this.attention.set(attention?.content ?? []);
      } else if (page === 'entities' && this.can('PUBLIC_ENTITY_READ')) {
        const result = await this.session.request<Page<PublicEntity>>(
          'GET',
          '/public-entities',
          undefined,
          {
            ...this.filters,
            page: String(this.listPage),
            size: '15',
          },
        );
        if (version === this.listVersion) this.entities.set(result);
      } else if (page === 'contracts' && this.can('CONTRACT_READ')) {
        const result = await this.session.request<Page<Contract>>('GET', '/contracts', undefined, {
          ...this.filters,
          page: String(this.listPage),
          size: '15',
        });
        if (version === this.listVersion) this.contracts.set(result);
      } else {
        this.error.set('Você não tem permissão para consultar este módulo.');
      }
    } catch (error) {
      if (version === this.listVersion) this.error.set(errorMessage(error));
    } finally {
      if (version === this.listVersion) this.loading.set(false);
    }
  }

  protected filter(event: Event): void {
    event.preventDefault();
    const data = new FormData(event.target as HTMLFormElement);
    this.filters = Object.fromEntries(
      Array.from(data.entries())
        .filter(([, value]) => value !== '')
        .map(([key, value]) => [key, String(value)]),
    );
    this.listPage = 0;
    void this.load();
  }

  protected paginate(offset: number): void {
    this.listPage += offset;
    void this.load();
  }

  protected openEntity(entity: PublicEntity | null = null): void {
    this.entityDialog.nativeElement.querySelector('form')?.reset();
    this.selectedEntity.set(entity ? { ...entity } : null);
    this.formError.set('');
    this.entityDialog.nativeElement.showModal();
  }

  protected openContract(contract: Contract): void {
    this.selectedContract.set(contract);
    this.contractDialog.nativeElement.showModal();
  }

  protected async saveEntity(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) return;
    const existing = this.selectedEntity();
    if (!this.can(existing ? 'PUBLIC_ENTITY_UPDATE' : 'PUBLIC_ENTITY_CREATE')) return;
    const form = event.target as HTMLFormElement;
    const data = Object.fromEntries(new FormData(form).entries());
    const body: Record<string, unknown> = { ...data };
    for (const field of ['cnpj', 'email', 'phone']) {
      if (!body[field]) body[field] = null;
    }
    if (existing) body['active'] = data['active'] === 'true';
    this.saving.set(true);
    this.formError.set('');
    try {
      await this.session.request(
        existing ? 'PUT' : 'POST',
        '/public-entities' + (existing ? '/' + existing.id : ''),
        body,
      );
      this.entityDialog.nativeElement.close();
      this.feedback.set(existing ? 'Órgão atualizado.' : 'Órgão cadastrado.');
      await this.load();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected async invite(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) return;
    const form = event.target as HTMLFormElement;
    const data = new FormData(form);
    this.saving.set(true);
    this.formError.set('');
    this.feedback.set('');
    this.invitationKey ??= crypto.randomUUID();
    try {
      await this.session.request(
        'POST',
        '/users/invitations',
        {
          name: String(data.get('name')),
          email: String(data.get('email')),
          roles: [String(data.get('role'))],
        },
        {},
        { 'Idempotency-Key': this.invitationKey },
      );
      this.invitationKey = null;
      form.reset();
      this.feedback.set('Convite enviado. O profissional receberá as instruções por e-mail.');
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected resetInvitation(): void {
    this.invitationKey = null;
  }

  protected async resendInvitation(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) return;
    const form = event.target as HTMLFormElement;
    const email = String(new FormData(form).get('email'));
    this.saving.set(true);
    this.resendError.set('');
    this.feedback.set('');
    try {
      await this.session.request('POST', '/users/invitations/resend', { email });
      form.reset();
      this.feedback.set('Convite reenviado. Confira o e-mail informado.');
    } catch (error) {
      this.resendError.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected expired(contract: Contract): boolean {
    const date = new Date(contract.endDate + 'T00:00:00');
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return date < today;
  }
}

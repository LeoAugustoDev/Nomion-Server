import { ChangeDetectionStrategy, Component, computed, inject, OnInit } from '@angular/core';
import { Session } from './session';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  protected readonly session = inject(Session);
  protected readonly selectedOffice = computed(() => {
    const tenantId = this.session.context()?.activeTenantId;
    return this.session.account()?.memberships.find((office) => office.tenantId === tenantId);
  });

  ngOnInit(): void {
    void this.session.initialize();
  }

  protected async selectOffice(event: Event): Promise<void> {
    const select = event.target as HTMLSelectElement;
    await this.session.loadProfile(select.value || undefined);
    select.value = this.selectedOffice()?.tenantId ?? '';
  }
}

import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';

async function start(): Promise<void> {
  const response = await fetch('/nomion-config.json', { cache: 'no-store' });
  if (!response.ok) {
    throw new Error('Configuração do Nomion indisponível');
  }
  const config: { keycloakUrl?: string } = await response.json();
  if (!config.keycloakUrl) {
    throw new Error('Endereço do Keycloak ausente');
  }
  await bootstrapApplication(App, appConfig(config.keycloakUrl));
}

void start().catch(() => {
  const root = document.querySelector('app-root');
  if (root) {
    root.textContent = 'Não foi possível iniciar o Nomion. Verifique a configuração do ambiente.';
    root.setAttribute('role', 'alert');
  }
});

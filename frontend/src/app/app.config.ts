import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KEYCLOAK } from './session';

export function appConfig(keycloakUrl: string): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideHttpClient(),
      {
        provide: KEYCLOAK,
        useFactory: () => new Keycloak({
          url: keycloakUrl,
          realm: 'nomion',
          clientId: 'nomion-angular',
        }),
      },
    ],
  };
}

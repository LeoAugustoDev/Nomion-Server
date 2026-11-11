# NOMION — Government Procurement Management Platform

<p align="center">
  <strong>A backend platform for managing public procurement processes and administrative workflows.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?logo=springboot" />
  <img src="https://img.shields.io/badge/PostgreSQL-Database-4169E1?logo=postgresql" />
  <img src="https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven" />
  <img src="https://img.shields.io/badge/Status-In%20Development-yellow" />
</p>

---

## Overview

**NOMION** is a backend platform designed to support the management of public procurement processes, administrative workflows, and institutional operations for Brazilian public institutions, including city halls and municipal legislative chambers.

The project aims to centralize administrative operations, improve information organization, and provide a structured foundation for managing procurement-related activities in accordance with Brazil's Public Procurement Law (Federal Law No. 14.133/2021).

The system is being developed with a focus on security, maintainability, scalability, and clean software architecture.

## Objectives

- Centralize public procurement information and administrative processes.
- Provide secure access to institutional resources.
- Establish a structured backend architecture.
- Support the evolution of procurement management workflows.
- Build a maintainable foundation for future enterprise features.

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 21 | Main programming language |
| Spring Boot 4 | Backend framework |
| Spring Security | Application security |
| Spring Data JPA | Data persistence |
| PostgreSQL | Relational database |
| Maven | Dependency management and build |
| JWT | Authentication |
| Git | Version control |

## Architecture

The application follows a layered backend architecture, separating responsibilities to improve maintainability, testability, and code organization.

```text
src/
└── main/
    ├── java/
    │   └── .../
    │       ├── config/
    │       ├── controller/
    │       ├── service/
    │       ├── repository/
    │       ├── entity/
    │       ├── dto/
    │       ├── security/
    │       └── exception/
    │
    └── resources/
        └── application.yml
```

*The directory structure above represents the intended architectural organization and should be adjusted to match the actual source code.*

## Security

### Keycloak for local development

Set `KEYCLOAK_ADMIN_PASSWORD` in your local `.env` before the first start. Use `.env.example` for the variable names. Compose starts Keycloak on demand and keeps its development database in a separate named volume.

```powershell
docker compose up -d keycloak
```

Open the [Keycloak administration console](http://localhost:8081/admin/) with the configured bootstrap credentials. The default issuer is `http://localhost:8081/realms/nomion`. If the port is unavailable, set `KEYCLOAK_PORT` in `.env` and use that port in both URLs. For local login, create a user in the `nomion` realm and set its password in the console.

The public client `nomion-angular` uses Authorization Code with PKCE S256 and redirects to `http://localhost:4200/`. Its access tokens include `nomion-api` in `aud`. The redirect and web origin must match the Angular development server.

Startup imports the [versioned realm](docker/keycloak/nomion-realm.json) only if that realm is absent. To apply later configuration changes to an existing local realm, update it in the console. The bootstrap credentials also apply only on first initialization. See the [Keycloak realm import documentation](https://www.keycloak.org/server/importExport).

The Compose service mounts the `nomion` login theme from `docker/keycloak/themes/nomion`. Existing realms must select `nomion` under Realm settings → Themes → Login theme. The theme inherits Keycloak's forms and password visibility script. Its login template is based on the bundled Keycloak 26.8.2 template and should be reviewed when upgrading Keycloak.

Google appears as a disabled option until an identity provider with alias `google` is configured. To enable it, create a Google OAuth web client with the redirect URI displayed in Keycloak's Google identity provider settings. Locally this is `http://localhost:8081/realms/nomion/broker/google/endpoint`, adjusted to your Keycloak port. Store the client ID and secret in the identity provider settings. For an existing Nomion user, link Google to that user's existing Keycloak identity so the subject and office memberships remain associated. [Google identity provider setup](https://www.keycloak.org/docs/26.8.0/server_admin/)

To run the API with Keycloak token validation, set the issuer to match your local Keycloak port and activate both Spring profiles:

```powershell
$env:KEYCLOAK_ISSUER_URI = 'http://localhost:8081/realms/nomion'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev,keycloak'
```

The `keycloak` profile validates the issuer, signature, expiration and `nomion-api` audience. Regular API requests require a local user whose `identity_issuer` and `identity_subject` match the verified token. Login, refresh and logout use Keycloak in this profile.

Call `GET /me` with the bearer token to get the local profile and active memberships in `user.memberships`. Send a single `X-Tenant-Id` header with the selected membership's `tenantId` on tenant requests. Calling `GET /me` with that header also returns `activeTenantId`, `roles` and `permissions` for the selected context. The API checks the active membership and loads permissions from the database on each request. An invalid header or unavailable membership returns HTTP 403.

#### First administrator and invitations

1. Create the first administrator in the `nomion` realm and verify the account's email. In `.env`, enable `INITIAL_SETUP_ENABLED`, set a random `INITIAL_SETUP_TOKEN` of at least 32 characters and set `INITIAL_SETUP_KEYCLOAK_SUBJECT` to the account's Keycloak user ID.
2. With that account's access token, call `POST /setup/initialize` with `Authorization: Bearer …`, `X-Setup-Token` and the JSON body below. The setup uses the verified email in the token. After HTTP 201, disable the setup and remove its temporary credentials.

```json
{"tenant":{"name":"First office","slug":"first-office"},"administratorName":"First administrator"}
```

For invitations, configure SMTP in the realm. Create a confidential client `nomion-provisioning` with service accounts enabled and interactive flows disabled. Assign its service account the realm's `realm-management` client role `manage-users` and assign the built-in `kc-admin-api` scope to the client as a default scope. Set `KEYCLOAK_PROVISIONING_CLIENT_SECRET` in the backend environment. Set `KEYCLOAK_PROVISIONING_CLIENT_ID` if you chose a different client ID. [Keycloak Admin REST API audience requirements](https://www.keycloak.org/docs/26.8.2/upgrading/)

For local development on Windows, start the isolated email inbox and configure the existing realm:

```powershell
docker compose --profile identity --profile mail up -d keycloak mailpit
.\docker\keycloak\configure-local-smtp.ps1
```

Open `http://localhost:8025` (or the port set by `MAILPIT_PORT`) to read invitation emails. Mailpit captures messages locally; it does not deliver them to real addresses. The configuration script replaces the realm's SMTP settings, so use it only for local development. For real delivery, configure your SMTP provider in Keycloak's Realm settings → Email and test it there. Existing realms retain their settings across restarts; reimporting the realm is not required. The Keycloak bootstrap administrator credentials in `.env` must match an existing master-realm administrator when running the script.

The invitation link expires after 24 hours and requests both email verification and password setup. It does not yet provide a passwordless Google-only acceptance option or a way to resend an expired invitation; linking an existing identity must follow Keycloak's account verification flow.

An administrator with `USER_CREATE` and `ROLE_ASSIGN` can call `POST /users/invitations` with the selected `X-Tenant-Id`, an `Idempotency-Key` UUID, and JSON containing `name`, `email` and `roles`. The endpoint returns HTTP 204 after Keycloak accepts the invitation email request. On a transient failure, repeat the request with the same UUID and body. The user and membership are saved before email delivery is requested. An ambiguous email failure can result in a second email on retry, while the identity and membership are reused. For an existing local user, use the membership endpoint with that user's UUID.

For an already initialized database, explicitly associate each existing user with the verified Keycloak issuer and subject while preserving their local UUID and memberships. At cutover, stop legacy instances before enabling the `keycloak` profile. In the application schema, revoke legacy refresh tokens and consume outstanding tenant selection tokens:

```sql
BEGIN;
UPDATE refresh_tokens SET revoked_at = CURRENT_TIMESTAMP, version = version + 1 WHERE revoked_at IS NULL;
UPDATE tenant_selection_tokens SET consumed_at = CURRENT_TIMESTAMP, version = version + 1 WHERE consumed_at IS NULL;
COMMIT;
```

### Angular frontend

The frontend runs at `http://localhost:4200/`. Start the API on port `8080` with the `dev,keycloak` profiles. Set up a linked Keycloak user with an active office membership before signing in.

```powershell
Set-Location frontend
if (-not (Test-Path public/nomion-config.json)) {
    Copy-Item nomion-config.example.json public/nomion-config.json
}
npm.cmd ci
npm.cmd start
```

Set `keycloakUrl` in `public/nomion-config.json` to the Keycloak origin, using the same port as the backend issuer. The Angular development server proxies `/api/*` to Spring without the `/api` prefix. A deployed frontend needs the same route on its own origin and matching redirect, logout and web origin settings on the Keycloak client. The browser config is public.

Run `npm.cmd test -- --watch=false` for the frontend tests and `npm.cmd run build` to produce the static files.

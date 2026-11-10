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

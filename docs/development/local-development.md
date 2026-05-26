# Development Guide

This guide is for contributors changing Cloud Archiver itself.

## Requirements

- Java 17
- Maven wrapper from this repository
- Docker, if you want to run the packaged service
- MongoDB and GCP credentials for integration-style local testing

## Common Commands

Run tests:

```bash
./mvnw test
```

Build the JAR:

```bash
./mvnw package
```

Run locally with the dev profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On Windows PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

## Code Map

| Package | Purpose |
|---------|---------|
| `config` | Configuration binding and Swagger setup |
| `controller` | REST API |
| `service` | Sync orchestration, catalog logic, notifications, locking |
| `cloudprovider` | Provider interface, factory, and implementations |
| `domain` | MongoDB documents and response DTOs |
| `repository` | Spring Data MongoDB repositories |
| `exception` | Domain exceptions |

For diagrams, see [Architecture](../architecture.md) and [Sync Workflow](../sync-workflow.md).

## Testing Notes

The unit tests cover controller behavior, catalog service logic, notification payloads, and GCP provider behavior with mocks. Real GCP and Atlas connectivity should be validated with a dedicated development configuration rather than production credentials.

Before changing sync behavior, review:

- [Backup Safety](../operations/backup-safety.md)
- [Sync Workflow](../sync-workflow.md)
- [Data Model](../data-model.md)

## Release Workflow

The project uses `gitflow-maven-plugin` with:

| Branch type | Pattern |
|-------------|---------|
| Production | `main` |
| Development | `develop` |
| Feature | `feature/*` |
| Release | `release/*` |
| Hotfix | `hotfix/*` |

Docker images are built by GitHub Actions for develop, release, and main workflows. See [Deployment](../deployment.md) for the existing CI/CD details.

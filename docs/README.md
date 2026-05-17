# Cloud Archiver — Documentation

Cloud Archiver is a self-hosted Spring Boot service that continuously synchronizes local folders to a cloud storage bucket. It tracks every file in a MongoDB catalog, uses CRC32C checksums to detect changes, and respects configurable retention windows to avoid early-deletion fees on cold storage classes (Nearline, Coldline, Archive).

---

## Documentation Index

| Document | Description |
|----------|-------------|
| [Architecture](architecture.md) | System overview, layer diagram, package structure |
| [Sync Workflow](sync-workflow.md) | Backup and cleanup flow with Mermaid diagrams |
| [Data Model](data-model.md) | MongoDB collections, entity fields, state transitions |
| [API Reference](api-reference.md) | REST endpoints, request/response examples |
| [Configuration](configuration.md) | All configuration properties and environment variables |
| [Cloud Provider](cloud-provider.md) | Provider abstraction, GCP implementation, dry-run mode |
| [Observability](observability.md) | Micrometer metrics, Prometheus endpoint, logging |
| [Deployment](deployment.md) | Docker Compose, CI/CD pipeline, GCP setup checklist |

---

## Quick Start

```mermaid
flowchart LR
    A["1. Create GCS bucket\n+ lifecycle rule"] --> B["2. Create service account\n+ download JSON key"]
    B --> C["3. Configure docker-compose.yml\n(env vars)"]
    C --> D["4. Mount volumes\n(scan folders + GCP key)"]
    D --> E["5. docker compose up -d"]
    E --> F["6. Check health\nGET /cloud-archiver/actuator/health"]
```

### Minimum required environment variables

```bash
APPLICATION_CLOUDPROVIDERCONFIG_TYPE=GCP
APPLICATION_CLOUDPROVIDERCONFIG_PROJECTID=<gcp-project-id>
APPLICATION_CLOUDPROVIDERCONFIG_BUCKETNAME=<bucket-name>
APPLICATION_CLOUDPROVIDERCONFIG_CREDENTIALSFILEPATH=/cloud-provider/key.json

APPLICATION_SCANFOLDERS_0_SCANFOLDER=/data/my-folder

SPRING_DATA_MONGODB_DATABASE=cloud_archiver
SPRING_DATA_MONGODB_URI=mongodb+srv://user:pass@host/
```

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Runtime | Java 17, Spring Boot 3.3.2 |
| Cloud storage | Google Cloud Storage (`google-cloud-storage` 2.36.1) |
| Database | MongoDB (Spring Data MongoDB) |
| Checksums | Guava CRC32C |
| Metrics | Micrometer + Prometheus |
| API docs | SpringDoc OpenAPI (Swagger UI) |
| Notifications | Discord webhook (WebFlux `RestTemplate`) |
| Container | Docker (Alpine + OpenJDK 17) |
| Build | Maven + gitflow-maven-plugin |
| CI/CD | GitHub Actions → Docker Hub |

---

## Version

Current version: `0.9.9.009-SNAPSHOT`  
Docker image: `ringuerel/cloud-archiver`

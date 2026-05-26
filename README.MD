# Cloud Archiver

Cloud Archiver is a self-hosted Spring Boot service for backing up local folders to cloud object storage. It catalogs every file in MongoDB, uses CRC32C checksums to detect content changes, and can optionally delete cloud objects after local files are removed while respecting cold-storage retention windows.

It is designed for homelab and personal-media archive workflows where the local disk remains the source of truth and the cloud bucket is the durable backup copy.

## Current Support

| Area | Support |
|------|---------|
| Runtime | Java 17, Spring Boot 3.3.2 |
| Packaging | Docker image, Maven build |
| Cloud storage | Google Cloud Storage |
| Database | MongoDB or MongoDB Atlas |
| Catalog lookup | REST API and Swagger UI |
| Metrics | Spring Actuator, Micrometer, Prometheus |
| Notifications | Discord-compatible webhook |

Current version: `0.10.0.001-SNAPSHOT`

## Start Here

New users should follow the documentation in this order:

1. [Getting Started](docs/getting-started.md) - first dry run, then first real GCP backup.
2. [Docker Compose Deployment](docs/install/docker-compose.md) - production-style container setup.
3. [GCP Setup](docs/install/gcp.md) - bucket, lifecycle, and service-account guidance.
4. [MongoDB Atlas Setup](docs/install/mongodb-atlas.md) - database setup and connection string.
5. [Backup Safety](docs/operations/backup-safety.md) - deletion rules, retention windows, and dry-run caveats.
6. [Restore Guide](docs/operations/restore-guide.md) - search the catalog and download files from cloud storage.
7. [Troubleshooting](docs/operations/troubleshooting.md) - common setup and runtime issues.

## Quick Start

Minimum environment variables for a real GCP-backed run:

```bash
APPLICATION_CLOUDPROVIDERCONFIG_TYPE=GCP
APPLICATION_CLOUDPROVIDERCONFIG_PROJECTID=<gcp-project-id>
APPLICATION_CLOUDPROVIDERCONFIG_BUCKETNAME=<bucket-name>
APPLICATION_CLOUDPROVIDERCONFIG_CREDENTIALSFILEPATH=/cloud-provider/key.json

APPLICATION_SCANFOLDERS_0_SCANFOLDER=/data/photos

SPRING_DATA_MONGODB_DATABASE=cloud_archiver
SPRING_DATA_MONGODB_URI=mongodb+srv://user:pass@host/
```

For a safe first validation, use `APPLICATION_CLOUDPROVIDERCONFIG_TYPE=NO_PROVIDER` with a temporary MongoDB database. Dry-run mode still writes catalog entries to MongoDB, so do not use the same database for dry-run and real upload unless you intentionally want that catalog state.

## Important Safety Defaults

| Setting | Default | Why it matters |
|---------|---------|----------------|
| `cleanRemovedFromCloud` | `false` | Local deletions do not delete cloud objects unless explicitly enabled. |
| `deleteIfEmptyEnabled` | `false` | Cleanup is skipped when a source folder is empty or missing, protecting against unmounted volumes. |
| `NO_PROVIDER` | opt-in | Walks folders and writes MongoDB catalog data without touching cloud storage. |
| `standardDeleteDaysLimit` / `archiveDeleteDaysHold` | unset | Lets you align cleanup with GCS lifecycle rules and avoid early-deletion fees. |

## Documentation Map

### User and Operator Docs

| Document | Purpose |
|----------|---------|
| [Getting Started](docs/getting-started.md) | First successful dry run and first real backup |
| [Docker Compose Deployment](docs/install/docker-compose.md) | Container configuration and volume mounts |
| [GCP Setup](docs/install/gcp.md) | Bucket, lifecycle, IAM, and credentials |
| [MongoDB Atlas Setup](docs/install/mongodb-atlas.md) | Atlas database setup |
| [Backup Safety](docs/operations/backup-safety.md) | Source-of-truth model, deletion behavior, retention |
| [Running Syncs](docs/operations/running-syncs.md) | Scheduled and manual sync behavior |
| [Restore Guide](docs/operations/restore-guide.md) | Search and download workflows |
| [Observability](docs/observability.md) | Metrics, health checks, and logging |
| [Troubleshooting](docs/operations/troubleshooting.md) | Common symptoms and fixes |

### Reference and Developer Docs

| Document | Purpose |
|----------|---------|
| [Configuration Reference](docs/configuration.md) | Full property and environment-variable list |
| [API Reference](docs/api-reference.md) | REST endpoints and examples |
| [Data Model](docs/data-model.md) | MongoDB collections and DTOs |
| [Architecture](docs/architecture.md) | System overview and package structure |
| [Sync Workflow](docs/sync-workflow.md) | Backup and cleanup sequence diagrams |
| [Cloud Provider Abstraction](docs/cloud-provider.md) | Provider strategy and GCP implementation |
| [Development Guide](docs/development/local-development.md) | Local build, tests, and release notes |

## Planned Features

- AWS and Azure cloud provider support
- Data deduplication
- Encryption before upload
- Web UI for configuration, monitoring, and file retrieval
- Resumable uploads for large files
- Retry and backoff for transient cloud API failures

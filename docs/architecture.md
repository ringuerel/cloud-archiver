# Cloud Archiver — Architecture

## Overview

Cloud Archiver is a self-hosted Spring Boot service that continuously synchronizes local folders to a cloud storage bucket. It tracks every file in a MongoDB catalog, computes CRC32C checksums to detect changes, uploads new or modified files, and optionally removes cloud objects when the local file is deleted — respecting configurable retention windows to avoid early-deletion fees on cold storage classes.

```mermaid
graph TB
    subgraph Host["Host / Docker Container"]
        FS["Local Filesystem\n(scan folders)"]
        APP["Cloud Archiver\nSpring Boot 3.3.2 / Java 17"]
    end

    subgraph Persistence["Persistence"]
        MONGO[("MongoDB Atlas\nfile_catalog\nsync_summary")]
    end

    subgraph Cloud["Cloud Storage"]
        GCP["GCP Cloud Storage\nBucket"]
    end

    subgraph Notifications["Notifications"]
        DISCORD["Discord Webhook"]
    end

    subgraph Observability["Observability"]
        PROM["Prometheus\n/actuator/prometheus"]
    end

    FS -->|"Files.walk()"| APP
    APP -->|"save / find"| MONGO
    APP -->|"upload / delete / download"| GCP
    APP -->|"POST webhook"| DISCORD
    APP -->|"metrics"| PROM
```

---

## Layer Diagram

```mermaid
graph LR
    subgraph API["REST Layer"]
        CTRL["FileCatalogController\n/cloud-archiver/file-catalog"]
    end

    subgraph Scheduling["Scheduling"]
        TT["TimedTask\n@Scheduled cron"]
    end

    subgraph Service["Service Layer"]
        FCS["FileCatalogService\n(FileCatalogServiceImpl)"]
        NS["NotificationService\n(WebhookNotificationService)"]
        SLM["SyncLockManager"]
        MAPPER["FileCatalogItemMapper"]
    end

    subgraph CloudAbstraction["Cloud Abstraction"]
        CPF["CloudProviderFactory"]
        GCP_P["GCPStorageProvider"]
        NO_P["NoProvider (dry-run)"]
    end

    subgraph Repository["Repository Layer"]
        REPO["FileCatalogItemRepository"]
        SREPO["SyncSummaryRepository"]
    end

    CTRL --> FCS
    TT --> FCS
    FCS --> SLM
    FCS --> MAPPER
    FCS --> CPF
    FCS --> REPO
    FCS --> SREPO
    FCS --> NS
    CPF --> GCP_P
    CPF --> NO_P
    REPO --> MONGO[("MongoDB")]
    SREPO --> MONGO
```

---

## Package Structure

```
com.homelab.ringue.cloud.archiver
├── CloudArchiverApplication.java       Entry point
├── config/
│   ├── ApplicationProperties.java      @ConfigurationProperties binding
│   └── SwaggerConfig.java              OpenAPI / Swagger UI setup
├── controller/
│   ├── FileCatalogController.java      REST endpoints
│   └── FileCatalogResponseEntityExceptionHandler.java  Global error handler
├── cloudprovider/
│   ├── CloudProvider.java              Interface: upload / delete / download / getCheckSum
│   ├── CloudProviderFactory.java       Selects provider by enum key
│   ├── CloudProviders.java             Enum: GCP | NO_PROVIDER
│   └── impl/
│       ├── GCPStorageProvider.java     Google Cloud Storage implementation
│       └── NoProvider.java             Dry-run / test implementation
├── domain/
│   ├── FileCatalogItem.java            MongoDB document (file_catalog)
│   └── SyncSummaryItem.java            MongoDB document (sync_summary)
├── exception/
│   ├── CloudBackupException.java       Checked — backup failure
│   └── CloudDeleteFailedException.java Runtime — cloud delete failure
├── repository/
│   ├── FileCatalogItemRepository.java  MongoRepository for file catalog
│   └── SyncSummaryRepository.java      MongoRepository for daily summaries
└── service/
    ├── FileCatalogService.java         Service interface
    ├── FileCatalogItemMapper.java      Mapper interface
    ├── NotificationService.java        Notification interface
    ├── SyncLockManager.java            Concurrency lock
    ├── TimedTask.java                  Scheduled trigger
    ├── impl/
    │   ├── FileCatalogServiceImpl.java Core business logic
    │   ├── FileCatalogItemMapperImpl.java  Path → domain object mapping
    │   └── WebhookNotificationService.java Discord webhook sender
    └── notification/
        ├── WebhookPayload.java         Webhook request body
        ├── Embed.java                  Discord embed object
        └── Field.java                 Discord embed field
```

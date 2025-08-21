# Cloud Archiver

## Table of Contents
1. [Introduction](#introduction)
2. [Features](#features)
3. [Configuration](#configuration)
4. [API Endpoints](#api-endpoints)
5. [Usage Examples](#usage-examples)
6. [Cloud Provider Setup](#cloud-provider-setup)
7. [MongoDB Setup](#mongodb-setup)
8. [Development Workflow](#development-workflow)
9. [Release Process](#release-process)
10. [Continuous Integration & Docker](#continuous-integration--docker)
11. [Docker Compose Example](#docker-compose-example)
12. [Planned Features](#planned-features)
13. [Support](#support)

---

## Introduction

Cloud Archiver is designed to provide a cost-effective, partial self-hosted backup solution by leveraging cloud storage for redundancy and safety. It scans configured folders, uploads new or changed files to the cloud, and manages deletions in alignment with cloud provider lifecycle policies.

> *Personal Note*: This project was born out of the need to convince my wife that our photos and videos were safe, even if stored on personal disks. By using a "big" cloud company for backup, I reduced costs from $10/month to ~$0.5/month for the same files.

![Sync workflow example](./docs/Sync%20workflow.png)
PlantUML Code to the diagram is [here](./docs/sync_workflow.md)

---

## Features

- Scan and upload files from configured folders to the cloud bucket.
- Detect and upload modified files.
- Optional deletion of cloud files when removed locally, respecting configured retention periods.
- MongoDB Atlas integration for metadata tracking and reporting.
- Configurable sync schedules using cron expressions.
- Retrieve archived files by date range and optional path filter.
- **NEW:** Download files from GCP by specifying a cloud path, restoring to a local folder.

---

## Configuration

### General

Configure the application using `application.yml` or environment variables.

#### Example for Download Root

```yaml
downloadsRoot: /home/user/downloads
```

This sets the root directory for restored files.

---

## API Endpoints

API documentation is available via Swagger UI:  
`http://host:8080/cloud-archiver/swagger-ui/index.html`

### GET /file-catalog
- **Description:** Search for files by name.
- **Parameters:** `fileName` (required)
- **Example:** `GET /file-catalog?fileName=myphoto`

### GET /file-catalog/similar
- **Description:** Find files with similar names.
- **Parameters:** `fileName` (required)
- **Example:** `GET /file-catalog/similar?fileName=document`

### GET /file-catalog/archived-range
- **Description:** List files archived within a date range, optionally filtered by path.
- **Parameters:** `startDate`, `endDate` (required), `path` (optional)
- **Example:** `GET /file-catalog/archived-range?startDate=2023-01-01&endDate=2023-12-31&path=/photos/vacation`

### POST /file-catalog/download
- **Description:** Download a file from the cloud to `downloadsRoot`.
- **Parameters:** `path` (required)
- **Example:** `POST /file-catalog/download?path=/home/user/test2/Authelia123.md`

### POST /file-catalog/sync
- **Description:** (Unsupported) Future endpoint for sync.

---

## Usage Examples

### Download from GCP

Restore files from your GCP bucket by providing the cloud path. Files are placed under `downloadsRoot`, preserving their relative path. Missing folders are created automatically.

---

## Cloud Provider Setup

Supported cloud providers:
- [Google Cloud Platform](./gcp/GCP-README.MD) (GCS Cloud Storage)

---

## MongoDB Setup

Create two collections:
- `file_catalog`: Stores files and metadata.
- `sync_summary`: Daily summary of uploads/deletes.

### MongoDB Atlas

MongoDB Atlas is a fully-managed cloud database.  
![Mongo Atlas report example](./mongo-atlas/ReportExample.png)

- [Mongo Atlas Setup](./mongo-atlas/MONGO-ATLAS-README.MD)

*Note*: You can use your own MongoDB instance, but Atlas-specific reports will not be available.

---

## Development Workflow

### Adding New Features with Gitflow Maven Plugin

1. Update local repo with `develop`.
2. Start a feature branch:
    ```
    mvn gitflow:feature-start -DfeatureName=your-feature-name
    ```
3. Implement and commit changes.
4. Finish the feature:
    ```
    mvn gitflow:feature-finish
    ```
5. Push changes:
    ```
    git push --all
    ```

---

## Release Process

Uses **gitflow-maven-plugin**:

1. Update local repo with `develop` and `main`.
2. Start a release:
    ```
    mvn gitflow:release-start
    ```
3. Update versions and commit.
4. Finish release:
    ```
    mvn gitflow:release-finish
    ```
5. Push changes and tags:
    ```
    git push --all && git push --tags
    ```

[Plugin documentation](https://github.com/aleksandr-m/gitflow-maven-plugin)

---

## Continuous Integration & Docker

On push to `develop`, GitHub Actions build the project and create a Docker image.

---

## Docker Compose Example

```yaml
# ...existing code...
  cloud-archiver:
    image: your-cloud-archiver-image
    environment:
      # ...existing code...
      DOWNLOADS_ROOT: /home/user/downloads
    # ...existing code...
```

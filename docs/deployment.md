# Deployment

## CI/CD Pipeline

Three GitHub Actions workflows build and push Docker images to Docker Hub.

```mermaid
gitGraph
    commit id: "feature work"
    branch develop
    checkout develop
    commit id: "merge feature"
    commit id: "push → develop" tag: "Docker: <version>"
    branch release/x.y.z
    checkout release/x.y.z
    commit id: "release candidate" tag: "Docker: <version>-RC"
    checkout main
    merge release/x.y.z id: "release" tag: "Docker: <version> + latest"
```

| Branch | Workflow | Docker tags pushed |
|--------|----------|--------------------|
| `develop` | `build-and-push-docker.yml` | `<version>` |
| `release/*` | `build-and-push-docker-release-candidate.yml` | `<version>` |
| `main` | `build-and-push-docker-main.yml` | `<version>` **and** `latest` |

All workflows:
1. Check out code
2. Set up JDK 17 (Temurin) with Maven cache
3. `mvn -B package`
4. Extract version from POM
5. Copy JAR to `src/main/docker/app.jar`
6. Build and push Docker image from `src/main/docker/Dockerfile`

---

## Docker Image

Base image: `alpine:latest` with `openjdk17`  
Runs as non-root user: `cloud-archiver`  
Exposed port: `8080`

```dockerfile
CMD ["java", "-jar", "-Dspring.config.location=./application.yml", "cloud-archiver.jar"]
```

The image bundles a default `application.yml`. All settings are overridden at runtime via environment variables.

---

## Docker Compose

Minimal working example:

```yaml
services:
  cloud-archiver:
    image: ringuerel/cloud-archiver:latest
    container_name: cloudarchiver
    environment:
      # Cloud provider
      - APPLICATION_CLOUDPROVIDERCONFIG_TYPE=GCP
      - APPLICATION_CLOUDPROVIDERCONFIG_PROJECTID=my-gcp-project
      - APPLICATION_CLOUDPROVIDERCONFIG_BUCKETNAME=my-backup-bucket
      - APPLICATION_CLOUDPROVIDERCONFIG_CREDENTIALSFILEPATH=/cloud-provider/gcp-key.json
      - APPLICATION_CLOUDPROVIDERCONFIG_STORAGECLASS=ARCHIVE

      # Scan folder
      - APPLICATION_SCANFOLDERS_0_SCANFOLDER=/data/photos
      - APPLICATION_SCANFOLDERS_0_CLEANREMOVEDFROMCLOUD=true
      - APPLICATION_SCANFOLDERS_0_IGNOREHIDDENFILES=true
      - APPLICATION_SCANFOLDERS_0_COLLECTIONFETCHSIZE=5000
      - APPLICATION_SCANFOLDERS_0_STANDARDDELETEDAYSLIMIT=30
      - APPLICATION_SCANFOLDERS_0_ARCHIVEDELETEDAYSHOLD=365

      # Schedule (daily at 3 AM)
      - BACKUP_SCHEDULE_CRON=0 0 3 * * *

      # MongoDB
      - SPRING_DATA_MONGODB_DATABASE=cloud_archiver
      - SPRING_DATA_MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/

      # Notifications (optional)
      - APPLICATION_NOTIFICATIONSCONFIG_URI=https://discord.com/api/webhooks/...
      - APPLICATION_NOTIFICATIONSCONFIG_USERNAME=cloud-archiver

    volumes:
      - /mnt/nas/photos:/data/photos
      - /secrets/gcp:/cloud-provider
    ports:
      - "8080:8080"
    restart: unless-stopped
```

---

## Volume Mounts

| Container path | Purpose |
|----------------|---------|
| Scan folder(s) | Local files to back up — must match `APPLICATION_SCANFOLDERS_N_SCANFOLDER` |
| GCP credentials dir | Directory containing the service account JSON key |
| Download root (optional) | Target for cloud-to-local downloads |

---

## GCP Setup Checklist

```mermaid
flowchart TD
    A["Create GCP Project\n(or reuse existing)"] --> B["Enable Cloud Storage API"]
    B --> C["Create GCS Bucket\n(globally unique name)"]
    C --> D["Choose region\nus-central1 has 5 GB free — good for dev"]
    D --> E["Set storage class to Standard\n(files start here)"]
    E --> F["Add lifecycle rule:\nStandard → Archive after N days\n(N = standardDeleteDaysLimit)"]
    F --> G["Disable public access\n(personal data)"]
    G --> H["Create Service Account\nwith Storage Object Admin role"]
    H --> I["Download JSON key file"]
    I --> J["Mount key into container\nset CREDENTIALSFILEPATH"]
```

**Recommended bucket settings for a personal media archive:**

| Setting | Recommended value | Reason |
|---------|------------------|--------|
| Region | Single region close to you | Lower cost than multi-region |
| Initial storage class | Standard | Free deletion within first 30 days |
| Lifecycle rule | Standard → Archive after 30 days | Lowest long-term storage cost |
| Public access | Prevented | Personal data |
| Versioning | Off | Not needed for backup-only use case |

> **Service account role:** `Storage Object Admin` on the bucket is sufficient. Avoid granting project-level `Editor` unless necessary.

---

## Dry-Run Mode

Set `APPLICATION_CLOUDPROVIDERCONFIG_TYPE=NO_PROVIDER` to run without any cloud interaction. The application will:
- Walk all configured folders
- Compute CRC32C checksums
- Write entries to MongoDB
- Log what *would* have been uploaded/deleted
- Send Discord notifications (if configured)

This is useful for validating configuration and estimating catalog size before committing to real cloud uploads.

---

## MongoDB Setup

Cloud Archiver requires two collections: `file_catalog` and `sync_summary`. Any MongoDB instance works, including a local one. [MongoDB Atlas](https://www.mongodb.com/cloud/atlas) offers a free 500 MB M0 tier that is sufficient for most homelab use cases.

### MongoDB Atlas setup

```mermaid
flowchart TD
    A["Sign up at mongodb.com/cloud/atlas"] --> B["Create a free M0 cluster\n(choose region close to you)"]
    B --> C["Create database\nname = your SPRING_DATA_MONGODB_DATABASE value"]
    C --> D["Create collection: file_catalog"]
    D --> E["Create collection: sync_summary"]
    E --> F["Click Connect → Connect your application"]
    F --> G["Copy SRV connection string\n→ SPRING_DATA_MONGODB_URI"]
```

**Connection string format:**
```
mongodb+srv://<username>:<password>@<cluster>.mongodb.net/<dbname>?retryWrites=true&w=majority
```

> **Note:** The free M0 tier stores small metadata per file. A library of ~100,000 files typically uses well under 500 MB.

---

## Development

```bash
# Run locally with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Build JAR
./mvnw package

# Run tests
./mvnw test
```

The `application-dev.yml` file configures local scan folders, a dev GCP project, and verbose logging (`TRACE` level for application code).

---

## Development Workflow (Gitflow)

The project uses [gitflow-maven-plugin](https://github.com/aleksandr-m/gitflow-maven-plugin) to manage branches and versions.

### Feature development

```bash
# 1. Pull latest develop
git pull origin develop

# 2. Start a feature branch (bumps version automatically)
mvn gitflow:feature-start -DfeatureName=your-feature-name

# 3. Implement and commit your changes

# 4. Finish the feature (merges back to develop)
mvn gitflow:feature-finish

# 5. Push
git push --all
```

### Release process

```bash
# 1. Pull latest develop and main
git pull origin develop
git pull origin main

# 2. Start a release branch
mvn gitflow:release-start

# 3. Update versions / changelog, commit

# 4. Finish the release (merges to main and develop, creates tag)
mvn gitflow:release-finish

# 5. Push branches and tags
git push --all && git push --tags
```

```mermaid
gitGraph
    commit id: "feature work"
    branch develop
    checkout develop
    commit id: "feature-finish"
    branch release/x.y.z
    checkout release/x.y.z
    commit id: "release prep"
    checkout main
    merge release/x.y.z id: "release" tag: "vX.Y.Z"
    checkout develop
    merge release/x.y.z id: "back-merge"
```

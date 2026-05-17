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
    A["Create GCP Project"] --> B["Enable Cloud Storage API"]
    B --> C["Create GCS Bucket"]
    C --> D["Configure bucket lifecycle\n(Standard → Archive after N days)"]
    D --> E["Create Service Account"]
    E --> F["Grant roles/storage.objectAdmin\nto service account"]
    F --> G["Download JSON key"]
    G --> H["Mount key into container\nset CREDENTIALSFILEPATH"]
```

1. Create a GCS bucket in your GCP project.
2. Set a lifecycle rule to transition objects from Standard to Archive (or Coldline/Nearline) after your `standardDeleteDaysLimit` days. This keeps storage costs low.
3. Create a service account with the `Storage Object Admin` role on the bucket.
4. Download the JSON key and mount it into the container.
5. Set `APPLICATION_CLOUDPROVIDERCONFIG_CREDENTIALSFILEPATH` to the mounted path.

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

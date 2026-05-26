# Docker Compose Deployment

Docker Compose is the recommended way to run Cloud Archiver in a homelab environment.

## Minimal Production Example

```yaml
services:
  cloud-archiver:
    image: ringuerel/cloud-archiver:latest
    container_name: cloudarchiver
    environment:
      - TZ=America/New_York

      - APPLICATION_CLOUDPROVIDERCONFIG_TYPE=GCP
      - APPLICATION_CLOUDPROVIDERCONFIG_PROJECTID=my-gcp-project
      - APPLICATION_CLOUDPROVIDERCONFIG_BUCKETNAME=my-backup-bucket
      - APPLICATION_CLOUDPROVIDERCONFIG_CREDENTIALSFILEPATH=/cloud-provider/key.json
      - APPLICATION_CLOUDPROVIDERCONFIG_STORAGECLASS=ARCHIVE

      - APPLICATION_SCANFOLDERS_0_SCANFOLDER=/data/photos
      - APPLICATION_SCANFOLDERS_0_CLEANREMOVEDFROMCLOUD=false
      - APPLICATION_SCANFOLDERS_0_DELETEIFEMPTYENABLED=false
      - APPLICATION_SCANFOLDERS_0_IGNOREHIDDENFILES=true
      - APPLICATION_SCANFOLDERS_0_COLLECTIONFETCHSIZE=5000
      - APPLICATION_SCANFOLDERS_0_STANDARDDELETEDAYSLIMIT=30
      - APPLICATION_SCANFOLDERS_0_ARCHIVEDELETEDAYSHOLD=365

      - BACKUP_SCHEDULE_CRON=0 0 3 * * *

      - SPRING_DATA_MONGODB_DATABASE=cloud_archiver
      - SPRING_DATA_MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/

      - APPLICATION_DOWNLOADROOT=/downloads
      - LOGGING_LEVEL_COM_HOMELAB_RINGUE=INFO

    volumes:
      - /mnt/photos:/data/photos:ro
      - /mnt/secrets/gcp:/cloud-provider:ro
      - /mnt/restore:/downloads
    ports:
      - "8080:8080"
    restart: unless-stopped
```

For a copyable starting point, see [.env.example](../../.env.example). It defaults to `NO_PROVIDER` and a dry-run MongoDB database so first runs do not touch cloud storage.

## Multiple Scan Folders

Add additional scan folders by incrementing the index:

```bash
APPLICATION_SCANFOLDERS_1_SCANFOLDER=/data/videos
APPLICATION_SCANFOLDERS_1_CLEANREMOVEDFROMCLOUD=false
APPLICATION_SCANFOLDERS_1_IGNOREHIDDENFILES=true
APPLICATION_SCANFOLDERS_1_COLLECTIONFETCHSIZE=5000
```

Each scan folder can have its own cleanup and retention settings. All configured folders use the same cloud provider and MongoDB database.

## Ignore Patterns

Ignore patterns are Java regular expressions matched against the file name:

```bash
APPLICATION_SCANFOLDERS_0_IGNOREPATTERNS_0=^\..+
APPLICATION_SCANFOLDERS_0_IGNOREPATTERNS_1=^_.*
APPLICATION_SCANFOLDERS_0_IGNOREPATTERNS_2=.+\.(mov|PNG)$
```

Use `APPLICATION_SCANFOLDERS_0_IGNOREHIDDENFILES=true` for normal dot-file filtering and add explicit ignore patterns only when you need extra rules.

## Useful URLs

| Purpose | URL |
|---------|-----|
| Health | `http://localhost:8080/cloud-archiver/actuator/health` |
| Prometheus metrics | `http://localhost:8080/cloud-archiver/actuator/prometheus` |
| Swagger UI | `http://localhost:8080/cloud-archiver/swagger-ui/index.html` |
| Manual sync | `POST http://localhost:8080/cloud-archiver/file-catalog/sync` |

## Existing Full Example

The repository also includes a larger commented example at [docker/cloud-archiver.yml](../../docker/cloud-archiver.yml).

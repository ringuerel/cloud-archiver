# Getting Started

This guide gets Cloud Archiver from a clean checkout to one verified backup workflow. Start with dry-run mode, then switch to GCP once the scan folder, MongoDB connection, and retention settings look right.

## 1. Prepare the Dependencies

You need:

- A folder on disk to back up.
- A MongoDB database for the file catalog.
- A GCS bucket and service-account JSON key for real uploads.
- Docker Compose for the recommended runtime.

For provider setup details, see:

- [GCP Setup](install/gcp.md)
- [MongoDB Atlas Setup](install/mongodb-atlas.md)
- [Docker Compose Deployment](install/docker-compose.md)

## 2. Run a Dry Run First

Dry-run mode uses `NO_PROVIDER`. It walks the folder, calculates CRC32C checksums, writes MongoDB catalog entries, logs what would happen, and sends notifications if configured. It does not upload or delete cloud objects.

Use a temporary MongoDB database for this first pass:

```yaml
services:
  cloud-archiver:
    image: ringuerel/cloud-archiver:latest
    environment:
      - APPLICATION_CLOUDPROVIDERCONFIG_TYPE=NO_PROVIDER
      - APPLICATION_SCANFOLDERS_0_SCANFOLDER=/data/photos
      - APPLICATION_SCANFOLDERS_0_CLEANREMOVEDFROMCLOUD=false
      - APPLICATION_SCANFOLDERS_0_IGNOREHIDDENFILES=true
      - SPRING_DATA_MONGODB_DATABASE=cloud_archiver_dry_run
      - SPRING_DATA_MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/
    volumes:
      - /mnt/photos:/data/photos:ro
    ports:
      - "8080:8080"
    restart: unless-stopped
```

Start the service:

```bash
docker compose up -d
```

Check health:

```bash
curl http://localhost:8080/cloud-archiver/actuator/health
```

Trigger a manual sync:

```bash
curl -X POST http://localhost:8080/cloud-archiver/file-catalog/sync
```

Search for a known file:

```bash
curl "http://localhost:8080/cloud-archiver/file-catalog/similar?fileName=photo"
```

## 3. Switch to Real GCP Uploads

When the dry run looks correct, switch to a real provider and a production MongoDB database:

```yaml
environment:
  - APPLICATION_CLOUDPROVIDERCONFIG_TYPE=GCP
  - APPLICATION_CLOUDPROVIDERCONFIG_PROJECTID=my-gcp-project
  - APPLICATION_CLOUDPROVIDERCONFIG_BUCKETNAME=my-backup-bucket
  - APPLICATION_CLOUDPROVIDERCONFIG_CREDENTIALSFILEPATH=/cloud-provider/key.json
  - APPLICATION_SCANFOLDERS_0_SCANFOLDER=/data/photos
  - APPLICATION_SCANFOLDERS_0_CLEANREMOVEDFROMCLOUD=false
  - SPRING_DATA_MONGODB_DATABASE=cloud_archiver
  - SPRING_DATA_MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/
volumes:
  - /mnt/photos:/data/photos:ro
  - /mnt/secrets/gcp:/cloud-provider:ro
```

Run another manual sync and confirm objects appear in the bucket.

## 4. Enable Cleanup Only After Backup Is Proven

Leave `APPLICATION_SCANFOLDERS_0_CLEANREMOVEDFROMCLOUD=false` until uploads, catalog searches, and restore tests are working.

When you intentionally want cloud objects deleted after local deletion, configure:

```bash
APPLICATION_SCANFOLDERS_0_CLEANREMOVEDFROMCLOUD=true
APPLICATION_SCANFOLDERS_0_STANDARDDELETEDAYSLIMIT=30
APPLICATION_SCANFOLDERS_0_ARCHIVEDELETEDAYSHOLD=365
```

Keep `APPLICATION_SCANFOLDERS_0_DELETEIFEMPTYENABLED=false` unless you explicitly want cleanup to run when the source folder is empty or missing.

## 5. Verify Restore

Set a download root:

```bash
APPLICATION_DOWNLOADROOT=/downloads
```

Mount it:

```yaml
volumes:
  - /mnt/restore:/downloads
```

Then download a known object:

```bash
curl -X POST "http://localhost:8080/cloud-archiver/file-catalog/download?path=/data/photos/example.jpg"
```

See [Restore Guide](operations/restore-guide.md) for folder restores and troubleshooting.

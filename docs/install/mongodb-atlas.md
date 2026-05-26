# MongoDB Atlas Setup

Cloud Archiver stores catalog metadata in MongoDB. MongoDB Atlas M0 is enough for many personal libraries because each file stores only metadata such as path, size, checksum, archive date, and modified time.

## Setup Checklist

1. Create an Atlas account.
2. Create a free M0 cluster.
3. Create a database, for example `cloud_archiver`.
4. Create the initial collections:
   - `file_catalog`
   - `sync_summary`
5. Create a database user.
6. Add the Cloud Archiver host IP or network to Atlas network access.
7. Copy the application connection string.

## Configuration

```bash
SPRING_DATA_MONGODB_DATABASE=cloud_archiver
SPRING_DATA_MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/
```

Atlas connection strings may include a database path. Cloud Archiver still uses `SPRING_DATA_MONGODB_DATABASE` to select the database name.

## Dry-Run Recommendation

Use a separate dry-run database, for example:

```bash
SPRING_DATA_MONGODB_DATABASE=cloud_archiver_dry_run
```

`NO_PROVIDER` does not touch cloud storage, but it still writes catalog records. Keeping dry-run data separate prevents confusion when switching to real uploads.

## Existing Visual Guide

The older Atlas walkthrough and screenshot are still available at [mongo-atlas/MONGO-ATLAS-README.MD](../../mongo-atlas/MONGO-ATLAS-README.MD).

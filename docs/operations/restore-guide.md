# Restore Guide

Cloud Archiver can search the MongoDB catalog and download files or folder prefixes from the cloud provider.

## Configure a Download Root

Set a local target directory:

```bash
APPLICATION_DOWNLOADROOT=/downloads
```

Mount it in Docker:

```yaml
volumes:
  - /mnt/restore:/downloads
```

Downloaded files are written under this root while preserving their object path structure.

## Find a File

Case-sensitive substring search:

```bash
curl "http://localhost:8080/cloud-archiver/file-catalog?fileName=photo.jpg"
```

Case-insensitive substring search:

```bash
curl "http://localhost:8080/cloud-archiver/file-catalog/similar?fileName=photo"
```

Search by archive date:

```bash
curl "http://localhost:8080/cloud-archiver/file-catalog/archived-range?startDate=2026-01-01&endDate=2026-01-31"
```

Add a path filter:

```bash
curl "http://localhost:8080/cloud-archiver/file-catalog/archived-range?startDate=2026-01-01&endDate=2026-01-31&path=/data/photos"
```

## Download One File

```bash
curl -X POST "http://localhost:8080/cloud-archiver/file-catalog/download?path=/data/photos/example.jpg"
```

## Download a Folder Prefix

Use a trailing slash:

```bash
curl -X POST "http://localhost:8080/cloud-archiver/file-catalog/download?path=/data/photos/2026/"
```

## Dry-Run Limitation

Downloads are not supported with `NO_PROVIDER`. Set `APPLICATION_CLOUDPROVIDERCONFIG_TYPE=GCP` and make sure the GCP key is mounted before using restore endpoints.

## Troubleshooting Restores

| Symptom | Check |
|---------|-------|
| HTTP 500 from download endpoint | Confirm `APPLICATION_DOWNLOADROOT` is configured and writable. |
| File not found in bucket | Confirm the path returned by catalog search matches the cloud object path. |
| Folder restore downloads nothing | Make sure the path ends with `/` and matches the object prefix. |
| Permission error | Confirm the service account has object read permissions on the bucket. |

# Troubleshooting

## Service Does Not Start

Check:

- `SPRING_DATA_MONGODB_URI` is valid.
- `SPRING_DATA_MONGODB_DATABASE` is set.
- `APPLICATION_CLOUDPROVIDERCONFIG_TYPE` is `GCP` or `NO_PROVIDER`.
- GCP credential path exists inside the container when using `GCP`.
- Port `8080` is available or remapped.

Health endpoint:

```bash
curl http://localhost:8080/cloud-archiver/actuator/health
```

## No Files Are Uploaded

Check:

- The scan folder path inside the container matches `APPLICATION_SCANFOLDERS_0_SCANFOLDER`.
- The volume is mounted and readable.
- Ignore patterns are not matching the files unexpectedly.
- The files were not previously cataloged during `NO_PROVIDER` dry-run mode in the same database.
- Logs at `DEBUG` show files being evaluated.

Enable more application logging:

```bash
LOGGING_LEVEL_COM_HOMELAB_RINGUE=DEBUG
```

## Cleanup Is Skipped

Cleanup runs only when:

```bash
APPLICATION_SCANFOLDERS_0_CLEANREMOVEDFROMCLOUD=true
```

If the source folder is empty or missing, cleanup is skipped unless:

```bash
APPLICATION_SCANFOLDERS_0_DELETEIFEMPTYENABLED=true
```

This protects against accidental mass deletion when a disk or network share is not mounted.

## Files Stay In Pending Deletion

Files may remain in pending deletion because retention settings are holding them:

```bash
APPLICATION_SCANFOLDERS_0_STANDARDDELETEDAYSLIMIT=30
APPLICATION_SCANFOLDERS_0_ARCHIVEDELETEDAYSHOLD=365
```

Check pending items:

```bash
curl http://localhost:8080/cloud-archiver/file-catalog/pending-deletion
```

`daysUntilDeletion` shows when each item becomes eligible.

## GCP Credential Errors

Check:

- The JSON key is mounted in the container.
- `APPLICATION_CLOUDPROVIDERCONFIG_CREDENTIALSFILEPATH` points to the container path, not the host path.
- The service account has bucket-level `Storage Object Admin`.
- The bucket name and project ID are correct.

## MongoDB Atlas Connection Errors

Check:

- Atlas network access allows the host IP.
- Username and password are URL-encoded in the connection string.
- The database user has read/write permissions.
- The URI starts with `mongodb+srv://` for Atlas SRV connections.

## Swagger UI Is Missing

Default URL:

```text
http://localhost:8080/cloud-archiver/swagger-ui/index.html
```

If the app is behind a reverse proxy, confirm the `/cloud-archiver` context path is preserved or rewritten consistently.

# Backup Safety

Cloud Archiver treats the local filesystem as the source of truth, MongoDB as the catalog, and the cloud bucket as the backup target.

## Source of Truth

During each sync, Cloud Archiver:

1. Walks each configured scan folder.
2. Looks up existing catalog entries in MongoDB.
3. Calculates CRC32C only for new or modified files.
4. Uploads new or changed files to the cloud provider.
5. Updates MongoDB catalog records.
6. Optionally deletes cloud objects for files that no longer exist locally.

Cloud cleanup only runs when `cleanRemovedFromCloud=true`.

## Deletion Controls

| Setting | Recommended starting value | Effect |
|---------|----------------------------|--------|
| `cleanRemovedFromCloud` | `false` | Prevents local deletions from deleting cloud objects. |
| `deleteIfEmptyEnabled` | `false` | Skips cleanup if the scan folder is missing or contains no files. |
| `standardDeleteDaysLimit` | match bucket lifecycle Standard period | Allows early cleanup while objects are still cheap to delete. |
| `archiveDeleteDaysHold` | match cold-storage minimum duration | Holds objects long enough to avoid early-deletion fees. |

The safest rollout is:

1. Run `NO_PROVIDER` against a temporary database.
2. Run `GCP` with `cleanRemovedFromCloud=false`.
3. Test restore.
4. Enable `cleanRemovedFromCloud=true` only after uploads and restores are proven.

## Empty Folder Guard

If a scan folder is empty or missing, Cloud Archiver assumes this may be an unmounted disk or broken volume mount. Cleanup is skipped unless:

```bash
APPLICATION_SCANFOLDERS_0_DELETEIFEMPTYENABLED=true
```

Keep this disabled unless you intentionally want an empty source folder to delete matching cloud objects.

## Retention Window

When retention settings are configured, the deletion eligibility date is:

```text
archiveDate + standardDeleteDaysLimit + archiveDeleteDaysHold
```

For example:

```bash
APPLICATION_SCANFOLDERS_0_STANDARDDELETEDAYSLIMIT=30
APPLICATION_SCANFOLDERS_0_ARCHIVEDELETEDAYSHOLD=365
```

A file uploaded on day 0 and deleted locally on day 100 remains in the catalog and cloud bucket until day 395.

## Dry-Run Caveat

`NO_PROVIDER` is safe for cloud costs, but it is not a no-write mode. It writes file catalog records to MongoDB. Use a temporary database or clear the catalog before switching the same data set to `GCP`.

## Checking Pending Deletions

Use the pending-deletion endpoint to see cataloged files that are missing on disk:

```bash
curl http://localhost:8080/cloud-archiver/file-catalog/pending-deletion
```

Filter by name or path:

```bash
curl "http://localhost:8080/cloud-archiver/file-catalog/pending-deletion?fileNameContains=vacation"
curl "http://localhost:8080/cloud-archiver/file-catalog/pending-deletion?path=/data/photos/2024"
```

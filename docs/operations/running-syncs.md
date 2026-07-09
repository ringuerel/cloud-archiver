# Running Syncs

Cloud Archiver can sync on a schedule or through the REST API.

## Scheduled Sync

The schedule is controlled by a Spring cron expression:

```bash
BACKUP_SCHEDULE_CRON=0 0 3/12 * * *
```

The default runs every 12 hours starting at 3 AM.

## Manual Sync

Trigger a sync immediately:

```bash
curl -X POST http://localhost:8080/cloud-archiver/file-catalog/sync
```

Expected success response:

```text
Sync process initiated successfully.
```

If another sync is already running, the endpoint returns HTTP `409 Conflict`.

## Locking Behavior

`SyncLockManager` allows only one sync at a time. If a previous run crashed and left the lock held, the lock is treated as stale after:

```bash
APPLICATION_SYNCLOCKTIMEOUTSECONDS=3600
```

The next sync after that timeout can force-release the stale lock and proceed.

## What One Sync Does

For each configured scan folder:

1. Load existing catalog entries for that folder.
2. Walk the source folder.
3. Skip ignored files and directories.
4. Upload files that are new or whose CRC32C changed.
5. Update metadata for files whose modified time changed but content did not.
6. Optionally run cleanup for local files that disappeared.
7. Save a daily sync summary.
8. Send a webhook summary if notifications are configured.

See [Sync Workflow](../sync-workflow.md) for sequence diagrams.

# Sync Workflow

## High-Level Flow

The sync process runs on a configurable cron schedule (default: 3 AM and 3 PM daily). It can also be triggered manually via the REST API. A `SyncLockManager` ensures only one sync runs at a time.

```mermaid
flowchart TD
    TRIGGER(["Cron trigger\nor POST /sync"])
    LOCK{"Acquire\nSyncLock?"}
    SKIP["Return 409 / skip"]
    RESET["Reset Micrometer metrics"]
    LOOP["For each ScanLocationConfig"]
    BACKUP["startCloudBackup()"]
    CLEAN{"cleanRemovedFromCloud\n= true?"}
    CLEANUP["startCloudCleanup()"]
    SUMMARY["addSummaryEntry()\n→ MongoDB sync_summary"]
    NOTIFY_SUMMARY["notifySummary()\n→ Discord webhook (async)"]
    RELEASE["Release SyncLock"]
    DONE(["Done"])

    TRIGGER --> LOCK
    LOCK -- No --> SKIP
    LOCK -- Yes --> RESET
    RESET --> LOOP
    LOOP --> BACKUP
    BACKUP --> CLEAN
    CLEAN -- Yes --> CLEANUP
    CLEAN -- No --> SUMMARY
    CLEANUP --> SUMMARY
    SUMMARY --> NOTIFY_SUMMARY
    NOTIFY_SUMMARY --> LOOP
    LOOP -- All done --> RELEASE
    RELEASE --> DONE
```

---

## Backup Phase Detail

For each configured scan folder, the service walks the filesystem and compares every file against the MongoDB catalog.

`FolderBackupServiceImpl` owns the scan/import pipeline: cache hydration, filtering, CRC32C decisions, upload execution, thumbnail creation, and metadata-only savebacks. After a successful upload, `ThumbnailService.createOrUpdateThumbnail()` is called immediately — the MongoDB save includes the thumbnail metadata in the same write. Thumbnail failures are non-blocking: they are recorded as `thumbnailStatus=FAILED` on the catalog item, and the upload is still counted as successful. Per-file upload decisions are wrapped in MDC fields (`scanFolder`, `filePath`, `backupDecisionId`) so structured logs survive the parallel stream fan-out without leaking context between files.

```mermaid
flowchart TD
    START(["startCloudBackup(scanFolder)"])
    CACHE["Load all catalog entries for\nthis folder into ConcurrentHashMap\n(paginated, collectionFetchSize)"]
    WALK["Files.walk(folder).parallel()"]
    MAP["mapFromPath()\n→ FileCatalogItem (no checksum yet)"]
    FILTER_RULES{"Pass filtering rules?\n(hidden files, ignore patterns)"}
    FILTER_DIR{"Is directory?"}
    CACHE_LOOKUP{"In memory cache?\n(by absolutePath)"}
    SAME_MTIME{"lastModified\nunchanged?"}
    CALC_CRC["Compute CRC32C\n(buffered, Guava)"]
    SAME_CRC{"CRC32C\nunchanged?"}
    UPDATE_MTIME["Update lastModified\nin cache only"]
    UPLOAD["cloudProvider.upload()\n→ GCP Storage"]
    THUMBNAIL["thumbnailService.createOrUpdateThumbnail()\n(non-blocking; records CREATED/SKIPPED/FAILED)"]
    SAVE_CATALOG["fileCatalogItemRepository.save()\n(includes thumbnail metadata)"]
    SAVE_REMAINING["Save remaining cache entries\n(metadata-only updates)"]
    END(["Backup complete"])

    START --> CACHE
    CACHE --> WALK
    WALK --> MAP
    MAP --> FILTER_RULES
    FILTER_RULES -- Ignored --> WALK
    FILTER_RULES -- Pass --> FILTER_DIR
    FILTER_DIR -- Directory --> WALK
    FILTER_DIR -- File --> CACHE_LOOKUP
    CACHE_LOOKUP -- Not in cache\n(new file) --> CALC_CRC
    CACHE_LOOKUP -- In cache --> SAME_MTIME
    SAME_MTIME -- Yes --> WALK
    SAME_MTIME -- No --> CALC_CRC
    CALC_CRC --> SAME_CRC
    SAME_CRC -- Yes --> UPDATE_MTIME
    UPDATE_MTIME --> WALK
    SAME_CRC -- No --> UPLOAD
    UPLOAD --> THUMBNAIL
    THUMBNAIL --> SAVE_CATALOG
    SAVE_CATALOG --> WALK
    WALK -- Stream exhausted --> SAVE_REMAINING
    SAVE_REMAINING --> END
```

---

## Cleanup Phase Detail

The cleanup phase removes cloud objects for files that no longer exist on disk. It respects configurable retention windows to avoid early-deletion fees on cold storage classes (Nearline, Coldline, Archive).

```mermaid
flowchart TD
    START(["startCloudCleanup(scanFolder)"])
    PAGE["Paginate catalog entries\nfor this folder\n(findByParentFolderStartsWith)"]
    DAYS{"standardDeleteDaysLimit\nconfigured?"}
    FILTER_DATE["Filter: archiveDate > (now - standardDeleteDays)\nOR archiveDate < (now - standardDeleteDays - archiveDeleteDaysHold)"]
    ALL["Fetch all entries\nfor this folder"]
    PARALLEL["Process entries in parallel"]
    EXISTS{"File exists\non disk?"}
    SKIP["Skip (file still present)"]
    DELETE_CLOUD["cloudProvider.delete()\n→ GCP Storage"]
    DELETE_CATALOG["fileCatalogItemRepository.delete()"]
    MORE{"More pages?"}
    END(["Cleanup complete"])

    START --> PAGE
    PAGE --> DAYS
    DAYS -- Yes --> FILTER_DATE
    DAYS -- No --> ALL
    FILTER_DATE --> PARALLEL
    ALL --> PARALLEL
    PARALLEL --> EXISTS
    EXISTS -- Yes --> SKIP
    EXISTS -- No --> DELETE_CLOUD
    DELETE_CLOUD --> DELETE_CATALOG
    DELETE_CATALOG --> DELETE_THUMB["thumbnailService.deleteThumbnail()\n(deletes local thumbnail file)"]
    DELETE_THUMB --> MORE
    SKIP --> MORE
    MORE -- Yes --> PAGE
    MORE -- No --> END
```

### Retention Window Logic

```mermaid
timeline
    title File lifecycle and deletion eligibility
    section Upload
        Day 0 : File uploaded to GCP (Standard class)
    section Standard window
        Day 0–30 : standardDeleteDaysLimit = 30
                 : File CAN be deleted from cloud if removed locally
    section Transition
        Day 30 : Bucket lifecycle moves file to Archive class
    section Archive hold
        Day 30–395 : archiveDeleteDaysHold = 365
                   : File NOT deleted from cloud even if removed locally
                   : (avoids early-deletion fees)
    section Free to delete
        Day 395+ : File eligible for cloud deletion again
```

---

## Detailed Sequence Diagram

This captures the full participant interaction across both backup and cleanup phases.

```mermaid
sequenceDiagram
    participant Scheduler as TimedTask (@Scheduled)
    participant Facade as SyncFacadeService
    participant Orchestrator as CloudSyncOrchestratorImpl
    participant Lock as SyncLockManager
    participant Backup as FolderBackupServiceImpl
    participant Thumbnail as ThumbnailService
    participant Cloud as CloudProvider
    participant DB as FileCatalogItemRepository
    participant SummaryDB as SyncSummaryRepository
    participant Notifier as NotificationService (async)

    Scheduler->>Facade: startAllLocationSyncs()
    Facade->>Orchestrator: startAllLocationSyncs()
    Orchestrator->>Lock: acquireLock()
    Lock-->>Orchestrator: true

    loop For each ScanLocationConfig
        Orchestrator->>Notifier: notifyInfoMessage("Started backup")

        Note over Backup,DB: ── Backup Phase ──
        Backup->>DB: findByParentFolderStartsWith() [paginated → ConcurrentHashMap]

        Backup->>Backup: Files.walk(scanFolder).parallel()

        loop For each file in stream
            Backup->>Backup: applyFilteringRules()
            Backup->>Backup: getFileToProcessIfAny()
            alt mtime unchanged
                Backup->>Backup: skip
            else mtime changed or new file
                Backup->>Backup: getCrC32C(file)
                alt CRC unchanged
                    Backup->>DB: update lastModified only
                else CRC changed or new
                    Backup->>Cloud: upload(fileCatalogItem)
                    Cloud-->>Backup: success
                    Backup->>Thumbnail: createOrUpdateThumbnail(item, false)
                    Thumbnail-->>Backup: item with thumbnail metadata
                    Backup->>DB: save(itemWithThumbnail)
                end
            end
        end
        Backup->>DB: save remaining cache entries (metadata-only)

        opt cleanRemovedFromCloud = true
            Note over Orchestrator,DB: ── Cleanup Phase ──
            Orchestrator->>Notifier: notifyInfoMessage("Started cleanup")
            loop For each page of catalog entries
                Orchestrator->>DB: findByParentFolderStartsWith() [paginated]
                loop For each entry missing from disk AND within deletion window
                    Orchestrator->>Cloud: delete(fileCatalogItem)
                    Cloud-->>Orchestrator: success
                    Orchestrator->>DB: delete(fileCatalogItem)
                    Orchestrator->>Thumbnail: deleteThumbnail(fileCatalogItem)
                end
            end
        end

        Orchestrator->>SummaryDB: save(SyncSummaryItem)
        Orchestrator->>Notifier: notifySummary(summary, config)
        Notifier-->>Orchestrator: (async, non-blocking)
    end

    Orchestrator->>Lock: releaseLock()
```

---

## Concurrency Control

```mermaid
sequenceDiagram
    participant Cron as Cron / REST
    participant SLM as SyncLockManager
    participant Facade as SyncFacadeService
    participant Orch as CloudSyncOrchestratorImpl

    Cron->>Facade: startAllLocationSyncs()
    Facade->>Orch: startAllLocationSyncs()
    Orch->>SLM: acquireLock(timeoutSeconds)
    alt Lock free
        SLM-->>Orch: true
        Orch->>Orch: run sync
        Orch->>SLM: releaseLock()
        Facade-->>Cron: true
    else Lock held and not stale
        SLM-->>Orch: false (skip)
        Facade-->>Cron: false
    else Lock held but stale (> timeout)
        SLM->>SLM: force releaseLock()
        SLM-->>Orch: true (proceeds)
    end
```

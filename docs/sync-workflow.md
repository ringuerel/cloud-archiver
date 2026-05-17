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
    SAVE_CATALOG["fileCatalogItemRepository.save()"]
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
    UPLOAD --> SAVE_CATALOG
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
    DELETE_CATALOG --> MORE
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

This captures the full participant interaction across both backup and cleanup phases, matching the original PlantUML workflow diagram.

```mermaid
sequenceDiagram
    participant Scheduler as TimedTask (@Scheduled)
    participant Service as FileCatalogServiceImpl
    participant Lock as SyncLockManager
    participant FS as File System
    participant Cloud as CloudProvider
    participant DB as FileCatalogItemRepository
    participant SummaryDB as SyncSummaryRepository
    participant Notifier as NotificationService (async)

    Scheduler->>Lock: acquireLock()
    Lock-->>Scheduler: true

    loop For each ScanLocationConfig
        Scheduler->>Service: performLocationSync(config)
        Service->>Notifier: notifyInfoMessage("Started backup")

        Note over Service,DB: ── Backup Phase ──
        Service->>DB: findByParentFolderStartsWith() [paginated]
        DB-->>Service: existing catalog items → ConcurrentHashMap

        Service->>FS: Files.walk(scanFolder).parallel()
        FS-->>Service: Stream of Paths

        loop For each file in stream
            Service->>Service: applyFilteringRules() — hidden / patterns
            Service->>Service: getFileToProcessIfAny()
            alt mtime unchanged
                Service->>Service: skip (no CRC needed)
            else mtime changed or new file
                Service->>FS: getCrC32C(file)
                FS-->>Service: CRC32C checksum
                alt CRC unchanged
                    Service->>DB: update lastModified only
                else CRC changed or new
                    Service->>Cloud: upload(fileCatalogItem)
                    Cloud-->>Service: success
                    Service->>DB: save(fileCatalogItem with archiveDate)
                end
            end
        end
        Service->>DB: save remaining cache entries (metadata updates)

        opt cleanRemovedFromCloud = true
            Note over Service,DB: ── Cleanup Phase ──
            Service->>Notifier: notifyInfoMessage("Started cleanup")
            loop For each page of catalog entries
                Service->>DB: findByParentFolderStartsWith() [paginated]
                DB-->>Service: catalog entries
                loop For each entry (parallel)
                    Service->>FS: Files.notExists(absolutePath)?
                    alt File missing from disk AND within deletion window
                        Service->>Cloud: delete(fileCatalogItem)
                        Cloud-->>Service: success
                        Service->>DB: delete(fileCatalogItem)
                    end
                end
            end
        end

        Service->>SummaryDB: save(SyncSummaryItem) [accumulates daily totals]
        Service->>Notifier: notifySummary(summary, config)
        Notifier-->>Service: (async, non-blocking)
    end

    Scheduler->>Lock: releaseLock()
```

---

## Concurrency Control

```mermaid
sequenceDiagram
    participant Cron as Cron / REST
    participant SLM as SyncLockManager
    participant SVC as FileCatalogServiceImpl

    Cron->>SLM: acquireLock(timeoutSeconds)
    alt Lock free
        SLM-->>Cron: true
        Cron->>SVC: startAllLocationSyncs()
        SVC-->>Cron: done
        Cron->>SLM: releaseLock()
    else Lock held and not stale
        SLM-->>Cron: false (skip)
    else Lock held but stale (> timeout)
        SLM->>SLM: force releaseLock()
        SLM-->>Cron: true (proceeds)
    end
```

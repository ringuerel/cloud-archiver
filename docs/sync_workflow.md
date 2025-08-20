# Cloud Archiver Sync Workflow

```plantuml
@startuml
title Cloud Archiver Sync Workflow

actor "Scheduler (TimedTask)" as Scheduler
participant "FileCatalogService" as Service
participant "FileCatalogServiceImpl" as Impl
participant "CloudProvider" as Cloud
participant "File System" as FS
participant "FileCatalogItemRepository" as DB
participant "SyncSummaryRepository" as SummaryDB
participant "NotificationService" as Notifier

Scheduler -> Impl: performScheduledBackup()
activate Impl

loop for each ScanLocationConfig
    Impl -> Service: performLocationSync(config)
    activate Service
    Service -> Impl: startCloudBackup(config)
    activate Impl

    Impl -> Impl: putCollectionIdsInMemoryCache(config)
    Impl -> DB: findByParentFolderStartsWith()
    activate DB
    DB --> Impl: Existing Catalog Items
    deactivate DB

    Impl -> FS: walk(scanFolder)
    activate FS
    FS --> Impl: Stream of Paths
    deactivate FS

    Impl -> Impl: processFileStreamForBackup()
    loop for each file in stream
        Impl -> Impl: getFileToProcessIfAny(fileOnDisk)
        Impl -> FS: getCrC32C(file)
        activate FS
        FS --> Impl: CRC32C Checksum
        deactivate FS
        alt File is new or modified
            Impl -> Impl: performCloudBackup(fileCatalogItem)
            Impl -> Cloud: upload(fileCatalogItem)
            activate Cloud
            Cloud --> Impl: Upload Success
            deactivate Cloud
            Impl -> DB: save(fileCatalogItem)
            activate DB
            DB --> Impl: Save Success
            deactivate DB
        end
    end
    Impl -> DB: save(updatedFileCatalogItems)
    activate DB
    DB --> Impl: Save Success
    deactivate DB

    alt cleanRemovedFromCloud is true
        Impl -> Impl: startCloudCleanup(config)
        Impl -> Impl: performBucketCleanup()
        loop for each page of catalog entries
            Impl -> DB: findByParentFolderStartsWith()
            activate DB
            DB --> Impl: Catalog Entries
            deactivate DB
            Impl -> Impl: processCatalogEntryForCleanup()
            loop for each catalog entry
                Impl -> FS: isFileNotExistOnDisk(file)
                activate FS
                FS --> Impl: File Exists?
                deactivate FS
                alt File does not exist on disk
                    Impl -> Impl: handleFileCatalogItemDelete(fileCatalogItem)
                    Impl -> Cloud: delete(fileCatalogItem)
                    activate Cloud
                    Cloud --> Impl: Delete Success
                    deactivate Cloud
                    Impl -> DB: delete(fileCatalogItem)
                    activate DB
                    DB --> Impl: Delete Success
                    deactivate DB
                end
            end
        end
    end

    Impl -> Impl: addSummaryEntry()
    Impl -> SummaryDB: save(syncSummaryItem)
    activate SummaryDB
    SummaryDB --> Impl: Save Success
    deactivate SummaryDB
    Impl -> Notifier: notifySummary(summary, config)
    activate Notifier
    Notifier --> Impl: Notification Sent
    deactivate Notifier

    deactivate Impl
    deactivate Service
end
deactivate Impl

@enduml

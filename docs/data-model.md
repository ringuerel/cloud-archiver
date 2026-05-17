# Data Model

Cloud Archiver uses two MongoDB collections.

---

## Collections

```mermaid
erDiagram
    FILE_CATALOG {
        string absolutePath PK "MongoDB _id — full local path"
        string fileName "e.g. photo.jpg"
        string fileExtension "e.g. jpg (nullable)"
        string parentFolder "used for prefix queries"
        boolean isDirectory
        long fileSize "bytes"
        date archiveDate "set on first upload; null if not yet uploaded"
        string crc32c "Base64-encoded CRC32C checksum"
        instant lastModified "filesystem mtime"
    }

    SYNC_SUMMARY {
        string syncDate PK "MM-dd-yyyy — one doc per day"
        int uploadCount "files uploaded that day"
        long uploadSize "bytes uploaded that day"
        int deleteCount "files deleted from cloud that day"
        long deleteSize "bytes deleted that day"
        instant lastUpdate "last write timestamp"
    }
```

---

## FileCatalogItem — State Transitions

```mermaid
stateDiagram-v2
    [*] --> Discovered : Files.walk() finds file

    Discovered --> New : Not in MongoDB catalog
    Discovered --> Unchanged : In catalog, same mtime
    Discovered --> Modified : In catalog, different mtime

    New --> ChecksumComputed : CRC32C calculated
    Modified --> ChecksumComputed : CRC32C calculated

    ChecksumComputed --> Uploaded : CRC32C differs from catalog\n(or no catalog entry)
    ChecksumComputed --> MetadataUpdated : CRC32C matches catalog\n(mtime changed but content same)

    Uploaded --> Cataloged : Saved to MongoDB with archiveDate
    MetadataUpdated --> Cataloged : lastModified updated in MongoDB

    Unchanged --> [*] : No action needed

    Cataloged --> DeletedFromCloud : File removed from disk\nAND within deletion window
    Cataloged --> Retained : File removed from disk\nBUT outside deletion window
    DeletedFromCloud --> [*] : Removed from MongoDB
    Retained --> DeletedFromCloud : Deletion window expires
```

---

## Repository Query Reference

### FileCatalogItemRepository

| Method | Purpose |
|--------|---------|
| `findById(absolutePath)` | Exact lookup by full path |
| `findByFileNameContains(fileName)` | Case-sensitive substring search |
| `findByFileNameContainsIgnoreCase(fileName)` | Case-insensitive substring search |
| `findByParentFolderStartsWith(parentFolder, pageable)` | All files under a folder (paginated) |
| `findByParentFolderStartsWithAndArchiveDateAfterOrParentFolderStartsWithAndArchiveDateBefore(...)` | Cleanup query: files uploaded recently OR very old |
| `findByArchiveDateBetweenAndAbsolutePathStartsWith(start, end, path)` | Date-range search under a path |
| `findByArchiveDateBetween(start, end)` | Date-range search across all paths |

### SyncSummaryRepository

Standard `MongoRepository<SyncSummaryItem, String>` — `findById(MM-dd-yyyy)` used to accumulate daily totals.

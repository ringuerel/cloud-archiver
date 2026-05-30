# Thumbnails

Cloud Archiver can create local, expendable thumbnails for cataloged media. The thumbnail path is stored in MongoDB on each `FileCatalogItem`, while the thumbnail file itself stays outside the archive bucket.

This is meant for restore discovery: if a media file was deleted locally but still exists in cold cloud storage, the catalog can show a local thumbnail so you can decide whether to restore it without reading the archived object first.

## Current Behavior

When thumbnails are enabled, Cloud Archiver creates generated thumbnails for supported image files after the original file uploads successfully.

Supported generated image extensions:

- `jpg`
- `jpeg`
- `png`
- `gif`
- `bmp`

The service stores:

- `thumbnailPath` - local thumbnail file path
- `thumbnailProvider` - currently `GENERATED`
- `thumbnailContentType`
- `thumbnailCreatedAt`
- `thumbnailStatus` - `CREATED`, `SKIPPED`, or `FAILED`
- `thumbnailError` - last failure or skip reason

`thumbnailPath` remains null when no thumbnail exists, so missing thumbnails are easy to query:

```javascript
db.file_catalog.find({
  $or: [
    { thumbnailPath: { $exists: false } },
    { thumbnailPath: null },
    { thumbnailPath: "" }
  ]
})
```

## Local Storage

Use a global thumbnail root for simple setups:

```yaml
application:
  thumbnails:
    enabled: true
    localRoot: /thumbnails
    maxWidth: 512
    maxHeight: 512
    outputFormat: jpg
```

Each managed scan location can override the root:

```yaml
application:
  scanFolders:
    - scanFolder: /immich/library
      thumbnailRoot: /thumbnails/immich-library
    - scanFolder: /external/photos
      thumbnailRoot: /thumbnails/external-photos
```

Cloud Archiver resolves thumbnail storage by longest matching scan-folder prefix. If no location-specific root is configured, it falls back to `application.thumbnails.localRoot`.

Generated thumbnail files are stored under hash-sharded folders such as `/thumbnails/e7/98/<sha256>.jpg`, rather than mirroring the original source path. This keeps cache directories from becoming too large, avoids collisions between common filenames like `IMG_0001.jpg`, avoids path-character issues, and keeps thumbnail lookup stable through the `thumbnailPath` stored in MongoDB. The tradeoff is that thumbnail folders are optimized as an application-managed cache, not as a human-browsable mirror of the source library.

## Rebuild Existing Thumbnails

Backfill thumbnails for existing catalog entries:

```bash
curl -X POST "http://localhost:8080/cloud-archiver/file-catalog/thumbnails/rebuild?mode=MISSING_ONLY&path=/immich/library"
curl -X POST "http://localhost:8080/cloud-archiver/file-catalog/thumbnails/rebuild?mode=FAILED_ONLY&limit=100&concurrency=4"
```

Available modes:

| Mode | Behavior |
|------|----------|
| `MISSING_ONLY` | Create thumbnails where `thumbnailPath` is missing, excluding entries already marked `SKIPPED` |
| `FAILED_ONLY` | Retry entries whose last thumbnail attempt failed |
| `FORCE` | Regenerate thumbnails even when one already exists |

Rebuild requires the source file to exist locally for generated thumbnails. If the original has already been deleted locally, a generated rebuild cannot create a thumbnail without another source such as Immich.

Thumbnail rebuilds process MongoDB pages sequentially, but can generate thumbnails in parallel within each page. The default worker count is `application.thumbnails.rebuild.maxConcurrency=2`; a rebuild request can override it with `concurrency`. Values are clamped between 1 and 16. Increase cautiously on hosts with enough CPU, memory, and disk throughput.

## Cleanup

Thumbnails are expendable local cache files, but they intentionally outlive the original local source file. When a source file is deleted locally, the MongoDB catalog entry can remain in a pending-deletion state until the configured retention window reaches EOL. During that period, Cloud Archiver keeps the thumbnail so restore-discovery views can still show a preview.

When Cloud Archiver eventually deletes the archived cloud object and removes the MongoDB catalog record, it also attempts to delete the local thumbnail file referenced by `thumbnailPath`. Thumbnail creation and deletion are logged with the `[THUMBNAIL]` prefix, similar to the cloud file lifecycle logs.

Deleting a local thumbnail file manually is safe. The catalog can rebuild it later if the original source file is still available.

## Video Thumbnail Plan

Video thumbnails should be added as a second generated provider capability, not mixed into the current image decoder path.

Recommended implementation:

1. Add video extensions to media detection:
   `mp4`, `mov`, `m4v`, `avi`, `mkv`, `webm`.
2. Add thumbnail config for video extraction:
   ```yaml
   application:
     thumbnails:
       video:
         enabled: false
         ffmpegPath: ffmpeg
         captureAtSeconds: 3
         timeoutSeconds: 30
   ```
3. Use `ffmpeg` to extract one frame into a temporary image:
   ```bash
   ffmpeg -y -ss 3 -i input.mp4 -frames:v 1 -vf scale=512:-1 output.jpg
   ```
4. Reuse the same local thumbnail path strategy and Mongo metadata fields.
5. Mark failures as `thumbnailStatus=FAILED` with the ffmpeg error message.
6. Keep video thumbnail failure non-blocking: original backup success must not depend on thumbnail generation.
7. Update the Docker image to include ffmpeg only if video thumbnails are enabled by default, or document that users need a custom image/runtime if it remains optional.
8. Add tests around command construction, timeout handling, unsupported files, and successful metadata update.

Immich thumbnail support can use the same catalog fields later, with `thumbnailProvider=IMMICH`.

package com.homelab.ringue.cloud.archiver.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.service.FileCatalogItemMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FileCatalogItemMapperImpl implements FileCatalogItemMapper {

    public FileCatalogItem mapFromPath(Path path){
        try{
            boolean isDirectory = Files.isDirectory(path);
            String fileName = path.getFileName().toString();
            String fileExtension = getFileExtension(fileName);
            Long fileSize = Files.size(path);
            return new FileCatalogItem(path.toAbsolutePath().toString(), fileName, fileExtension, path.toAbsolutePath().getParent().toString(), isDirectory, fileSize,null,null,Files.getLastModifiedTime(path).toInstant());
        }catch(Exception e){
            log.error("Failed converting {} to a valid FileCatalogItem",path, e);
        }
        return null;
    }

    private static String getFileExtension(String fileName) {
        int lastIndex = fileName.lastIndexOf('.');
        if (lastIndex != -1 && lastIndex != 0) {
            return fileName.substring(lastIndex + 1);
        } else {
            return null; // No file extension found
        }
    }

    @Override
    public FileCatalogItem mapFromFileCatalogItemAddArchiveDate(FileCatalogItem fileCatalogItem) {
        return copyWith(fileCatalogItem, fileCatalogItem.crc32c(), fileCatalogItem.lastModified(), new Date(),
                fileCatalogItem.thumbnailPath(), fileCatalogItem.thumbnailProvider(), fileCatalogItem.thumbnailContentType(),
                fileCatalogItem.thumbnailCreatedAt(), fileCatalogItem.thumbnailStatus(), fileCatalogItem.thumbnailError());
    }

    @Override
    public FileCatalogItem mapFromFileCatalogItemUpdateCheckSum(FileCatalogItem fileCatalogItem, String checkSum) {
        return copyWith(fileCatalogItem, checkSum, fileCatalogItem.lastModified(), fileCatalogItem.archiveDate(),
                fileCatalogItem.thumbnailPath(), fileCatalogItem.thumbnailProvider(), fileCatalogItem.thumbnailContentType(),
                fileCatalogItem.thumbnailCreatedAt(), fileCatalogItem.thumbnailStatus(), fileCatalogItem.thumbnailError());
    }

    @Override
    public FileCatalogItem mapFromFileCatalogItemUpdateLastModified(FileCatalogItem fileCatalogItem, Instant lastModified) {
        return copyWith(fileCatalogItem, fileCatalogItem.crc32c(), lastModified, fileCatalogItem.archiveDate(),
                fileCatalogItem.thumbnailPath(), fileCatalogItem.thumbnailProvider(), fileCatalogItem.thumbnailContentType(),
                fileCatalogItem.thumbnailCreatedAt(), fileCatalogItem.thumbnailStatus(), fileCatalogItem.thumbnailError());
    }

    @Override
    public FileCatalogItem mapFromFileCatalogItemUpdateThumbnail(
            FileCatalogItem fileCatalogItem,
            String thumbnailPath,
            String thumbnailProvider,
            String thumbnailContentType,
            Instant thumbnailCreatedAt,
            String thumbnailStatus,
            String thumbnailError) {
        return copyWith(fileCatalogItem, fileCatalogItem.crc32c(), fileCatalogItem.lastModified(), fileCatalogItem.archiveDate(),
                thumbnailPath, thumbnailProvider, thumbnailContentType, thumbnailCreatedAt, thumbnailStatus, thumbnailError);
    }

    private FileCatalogItem copyWith(
            FileCatalogItem fileCatalogItem,
            String crc32c,
            Instant lastModified,
            Date archiveDate,
            String thumbnailPath,
            String thumbnailProvider,
            String thumbnailContentType,
            Instant thumbnailCreatedAt,
            String thumbnailStatus,
            String thumbnailError) {
        return new FileCatalogItem(
                fileCatalogItem.absolutePath(),
                fileCatalogItem.fileName(),
                fileCatalogItem.fileExtension(),
                fileCatalogItem.parentFolder(),
                fileCatalogItem.isDirectory(),
                fileCatalogItem.fileSize(),
                archiveDate,
                crc32c,
                lastModified,
                thumbnailPath,
                thumbnailProvider,
                thumbnailContentType,
                thumbnailCreatedAt,
                thumbnailStatus,
                thumbnailError);
    }

}

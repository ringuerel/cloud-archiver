package com.homelab.ringue.cloud.archiver.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
            LocalDateTime lastModified= Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return new FileCatalogItem(path.toAbsolutePath().toString(), fileName, fileExtension, path.toAbsolutePath().getParent().toString(), isDirectory, fileSize,null,null,lastModified);
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
        return new FileCatalogItem(fileCatalogItem.absolutePath(), fileCatalogItem.fileName(), fileCatalogItem.fileExtension(), fileCatalogItem.parentFolder(), fileCatalogItem.isDirectory(), fileCatalogItem.fileSize(), new Date(),fileCatalogItem.crc32c(),fileCatalogItem.lastModified());
    }

    @Override
    public FileCatalogItem mapFromFileCatalogItemUpdateCheckSum(FileCatalogItem fileCatalogItem, String checkSum) {
        return new FileCatalogItem(fileCatalogItem.absolutePath(), fileCatalogItem.fileName(), fileCatalogItem.fileExtension(), fileCatalogItem.parentFolder(), fileCatalogItem.isDirectory(), fileCatalogItem.fileSize(), fileCatalogItem.archiveDate(),checkSum,fileCatalogItem.lastModified());
    }

    @Override
    public FileCatalogItem mapFromFileCatalogItemUpdateLastModified(FileCatalogItem fileCatalogItem, LocalDateTime lastModified) {
        return new FileCatalogItem(fileCatalogItem.absolutePath(), fileCatalogItem.fileName(), fileCatalogItem.fileExtension(), fileCatalogItem.parentFolder(), fileCatalogItem.isDirectory(), fileCatalogItem.fileSize(), fileCatalogItem.archiveDate(),fileCatalogItem.crc32c(),lastModified);
    }

}

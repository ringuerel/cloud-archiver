package com.homelab.ringue.cloud.archiver.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;

public interface FolderBackupService {

    BackupPipelineContext backUpFolder(ScanLocationConfig locationConfig) throws CloudBackupException;

    void processFileStreamForBackup(ScanLocationConfig locationConfig,
            BackupPipelineContext context,
            Stream<Path> filesStream);

    String getCrC32C(String absolutePath) throws IOException;
}

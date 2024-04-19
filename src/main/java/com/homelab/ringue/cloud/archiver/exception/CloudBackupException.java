package com.homelab.ringue.cloud.archiver.exception;

public class CloudBackupException extends Exception{

    public CloudBackupException(String rootFolder, Exception e) {
        super("Failed during cloud backup for "+rootFolder, e);
    }

}

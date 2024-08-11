package com.homelab.ringue.cloud.archiver.service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;

public interface NotificationService {

    void notifySummary(SyncSummaryItem summary, ScanLocationConfig scanlocationconfig);

    void notifyError(String message);

    void notifyInfoMessage(String message);
    
    void sendMessageFromTemplate(String messageTemplate, SyncSummaryItem syncSummaryItem,ScanLocationConfig scanlocationconfig);

}

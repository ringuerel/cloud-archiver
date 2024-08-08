package com.homelab.ringue.cloud.archiver.service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;

public interface NotificationService {

    void notifySummary(SyncSummaryItem summary, ScanLocationConfig scanlocationconfig);

    void notifyError(String message);

}

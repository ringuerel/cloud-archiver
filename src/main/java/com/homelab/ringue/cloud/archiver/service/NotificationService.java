package com.homelab.ringue.cloud.archiver.service;

import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;

public interface NotificationService {

    void notifySummary(SyncSummaryItem summary);

    void notifyError(String message);

}

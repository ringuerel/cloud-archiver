package com.homelab.ringue.cloud.archiver.service.impl;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.NotificationsConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;
import com.homelab.ringue.cloud.archiver.service.NotificationService;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Async
@Slf4j
public class WebhookNotificationService implements NotificationService{

    private NotificationsConfig notificationsConfig;

    public WebhookNotificationService(ApplicationProperties applicationProperties){
        this.notificationsConfig = applicationProperties.getNotificationsConfig();
    }

    @Data
    class WebhookPayload{
        private String username;
        private String content;
    }

    public static String humanReadableByteCountSI(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }

    @Override
    public void notifySummary(SyncSummaryItem summary,ScanLocationConfig scanlocationconfig) {
        if(notificationsConfig == null){
            return;
        }
        sendWebhookMessage(String.format("%s %d files for %s\n%s %d files for %s, location: %s",notificationsConfig.getNewItemsText(),summary.uploadCount(),humanReadableByteCountSI(summary.uploadSize()), notificationsConfig.getDeletedItemsText(),summary.deleteCount(),humanReadableByteCountSI(summary.deleteSize()),scanlocationconfig.getScanFolder()));
    }

    private void sendWebhookMessage(String webhookMessage){
        WebhookPayload payload = new WebhookPayload();
        payload.setUsername(notificationsConfig.getUserName());
        log.debug("About to send webhook message {}",webhookMessage);
        payload.setContent(webhookMessage);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.postForEntity(notificationsConfig.getUri(), payload, String.class);
    }

    @Override
    public void notifyError(String message) {
        if(notificationsConfig == null){
            return;
        }
        sendWebhookMessage(":x: "+message);
    }

}

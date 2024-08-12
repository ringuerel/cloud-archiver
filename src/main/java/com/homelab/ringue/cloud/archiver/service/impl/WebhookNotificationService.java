package com.homelab.ringue.cloud.archiver.service.impl;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    public WebhookNotificationService(ApplicationProperties applicationProperties,RestTemplateBuilder restTemplateBuilder){
        this.notificationsConfig = applicationProperties.getNotificationsConfig();
        this.restTemplateBuilder = restTemplateBuilder;
    }

    @Data
    class WebhookPayload{
        private String username;
        private String content;
    }

    String humanReadableByteCountSI(long bytes) {
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
        sendMessageFromTemplate(notificationsConfig.getSummaryTemplateText(), summary, scanlocationconfig);
    }

    void sendWebhookMessage(String webhookMessage){
        WebhookPayload payload = new WebhookPayload();
        payload.setUsername(notificationsConfig.getUserName());
        log.debug("About to send webhook message {}",webhookMessage);
        payload.setContent(webhookMessage);
        restTemplateBuilder.build().postForEntity(notificationsConfig.getUri(), payload, String.class);
    }

    @Override
    public void notifyError(String message,ScanLocationConfig scanLocationConfig) {
        if(notificationsConfig == null){
            return;
        }
        sendMessageFromTemplate(Optional.ofNullable(notificationsConfig.getErrorPRefix()).orElse(ApplicationProperties.NotificationsConfig.DEFAULT_ERROR_PREFIX)+ApplicationProperties.NotificationsConfig.SCAN_LOCATION+": "+message,null,scanLocationConfig);
    }

    @Override
    public void notifyInfoMessage(String message,ScanLocationConfig scanLocationConfig) {
        if(notificationsConfig == null){
            return;
        }
        sendMessageFromTemplate(Optional.ofNullable(notificationsConfig.getInfoPrefix()).orElse(ApplicationProperties.NotificationsConfig.DEFAULT_INFO_PREFIX)+ApplicationProperties.NotificationsConfig.SCAN_LOCATION+": "+message,null,scanLocationConfig);
    }

    void sendMessageFromTemplate(String messageTemplate, SyncSummaryItem syncSummaryItem,ScanLocationConfig scanlocationconfig) {
        sendWebhookMessage(replaceMessageInTemplate(messageTemplate,syncSummaryItem,scanlocationconfig));
    }

    protected String replaceMessageInTemplate(String messageTemplate, SyncSummaryItem syncSummaryItem,
            ScanLocationConfig scanlocationconfig) {
                Optional<String> replacedResult = Optional.ofNullable(syncSummaryItem).map(syncSummary->{
                    String result = messageTemplate.replace(ApplicationProperties.NotificationsConfig.IMPORTED_COUNT, String.valueOf(syncSummary.uploadCount()))
                    .replace(ApplicationProperties.NotificationsConfig.IMPORTED_SIZE, humanReadableByteCountSI(syncSummary.uploadSize()))
                    .replace(ApplicationProperties.NotificationsConfig.DELETED_COUNT, String.valueOf(syncSummary.deleteCount()))
                    .replace(ApplicationProperties.NotificationsConfig.DELETED_SIZE, humanReadableByteCountSI(syncSummary.deleteSize()));
                    return result;
                });
        return replacedResult.orElse(messageTemplate).replace(ApplicationProperties.NotificationsConfig.SCAN_LOCATION, scanlocationconfig.getScanFolder());
    }

}

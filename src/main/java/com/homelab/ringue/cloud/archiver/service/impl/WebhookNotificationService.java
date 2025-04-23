package com.homelab.ringue.cloud.archiver.service.impl;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.homelab.ringue.cloud.archiver.service.notification.Embed;
import com.homelab.ringue.cloud.archiver.service.notification.Field;
import com.homelab.ringue.cloud.archiver.service.notification.WebhookPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.NotificationsConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;
import com.homelab.ringue.cloud.archiver.service.NotificationService;

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
        presentAndSendMessage(notificationsConfig.getSummaryTemplateText(), summary, scanlocationconfig);
    }

    void sendWebhookMessage(WebhookPayload webhookPayload){
        restTemplateBuilder.build().postForEntity(notificationsConfig.getUri(), webhookPayload, String.class);
    }

    @Override
    public void notifyError(String message,ScanLocationConfig scanLocationConfig) {
        if(notificationsConfig == null){
            return;
        }
        presentAndSendMessage(Optional.ofNullable(notificationsConfig.getErrorPrefix()).orElse(ApplicationProperties.NotificationsConfig.DEFAULT_ERROR_PREFIX)+ApplicationProperties.NotificationsConfig.SCAN_LOCATION+": "+message,null,scanLocationConfig);
    }

    @Override
    public void notifyInfoMessage(String message,ScanLocationConfig scanLocationConfig) {
        if(notificationsConfig == null){
            return;
        }
        presentAndSendMessage(Optional.ofNullable(notificationsConfig.getInfoPrefix()).orElse(ApplicationProperties.NotificationsConfig.DEFAULT_INFO_PREFIX)+ApplicationProperties.NotificationsConfig.SCAN_LOCATION+": "+message,null,scanLocationConfig);
    }

    void presentAndSendMessage(String messageTemplate, SyncSummaryItem syncSummaryItem, ScanLocationConfig scanlocationconfig) {
        WebhookPayload.WebhookPayloadBuilder webhookPayloadBuilder = WebhookPayload.builder().username(notificationsConfig.getUserName());
        if(notificationsConfig.isEmbedEnabled()){
            log.info("Presenting message with embed");
            if(syncSummaryItem == null){
                log.error("Invalid (null) syncSummaryItem was passed to notify");
                syncSummaryItem = new SyncSummaryItem(Instant.now().toString(),0,0,0,0, Instant.now());
            }
            webhookPayloadBuilder.embed(Embed.builder()
                    .color("65280")//Green
                    .title(scanlocationconfig.getScanFolder())
                    .field(Field.builder().name("Uploaded files").value(Optional.ofNullable(syncSummaryItem.uploadCount()).orElse(0).toString())
                            .inline(true)
                            .build())
                    .field(Field.builder().name("Size").value(humanReadableByteCountSI(syncSummaryItem.uploadSize()))
                            .inline(true)
                            .build())
                    .build())
            .embed(Embed.builder()
                    .color("16711680")//Red
                    .title(scanlocationconfig.getScanFolder())
                    .field(Field.builder().name("Deleted files").value(Optional.ofNullable(syncSummaryItem.deleteCount()).orElse(0).toString())
                            .inline(true)
                            .build())
                    .field(Field.builder().name("Size").value(humanReadableByteCountSI(syncSummaryItem.deleteSize()))
                            .inline(true)
                            .build())
                    .build());
        }else{
            webhookPayloadBuilder.content(replaceMessageInTemplate(messageTemplate,syncSummaryItem,scanlocationconfig));
        }
        WebhookPayload webhookPayload = webhookPayloadBuilder.build();
        sendWebhookMessage(webhookPayload);
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

package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.slf4j.MDC;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.NotificationsConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;
import com.homelab.ringue.cloud.archiver.service.CloudSyncContext;
import com.homelab.ringue.cloud.archiver.service.notification.WebhookPayload;

@ExtendWith(MockitoExtension.class)
class WebhookNotificationServiceTest {

    private WebhookNotificationService notificationService;
    private NotificationsConfig notificationsConfig;

    @BeforeEach
    void init() {
        ApplicationProperties applicationProperties = new ApplicationProperties();
        notificationsConfig = new NotificationsConfig();
        notificationsConfig.setUserName("Cloud Archiver");
        applicationProperties.setNotificationsConfig(notificationsConfig);
        notificationService = Mockito.spy(new WebhookNotificationService(applicationProperties, Mockito.mock(RestTemplateBuilder.class)));
    }

    @AfterEach
    void clearMdc() {
        CloudSyncContext.clear();
    }

    @ParameterizedTest
    @MethodSource("messageTemplateTestProvider")
    void testPresentAndSendMessageShouldInvokeReplaceMessageInTemplate(String messageTemplate,
            SyncSummaryItem syncSummaryItem,
            ScanLocationConfig scanlocationconfig) {
        Mockito.doNothing().when(notificationService).sendWebhookMessage(Mockito.any(WebhookPayload.class));
        notificationService.presentAndSendMessage(messageTemplate, syncSummaryItem, scanlocationconfig);
        Mockito.verify(notificationService).replaceMessageInTemplate(messageTemplate, syncSummaryItem,
                scanlocationconfig);
    }

    @ParameterizedTest
    @MethodSource("messageTemplateTestProvider")
    void testPresentAndSendMessageShouldUseEmbed(String messageTemplate,
            SyncSummaryItem syncSummaryItem,
            ScanLocationConfig scanlocationconfig) {
        if (syncSummaryItem != null) {
            Mockito.doNothing().when(notificationService).sendWebhookMessage(Mockito.any(WebhookPayload.class));
        }
        notificationsConfig.setEmbedEnabled(true);
        notificationService.presentAndSendMessage(messageTemplate, syncSummaryItem, scanlocationconfig);
        if (syncSummaryItem != null) {
            ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);
            Mockito.verify(notificationService).sendWebhookMessage(captor.capture());
            Assertions.assertNotNull(captor.getValue().getEmbeds());
        } else {
            Mockito.verify(notificationService, Mockito.times(0)).sendWebhookMessage(Mockito.any());
        }
    }

    @Test
    void sendWithMdcContextClearsMdcAfterSending() {
        ScanLocationConfig scanLocationConfig = new ScanLocationConfig();
        scanLocationConfig.setScanFolder("/var/scanfolder/");
        Mockito.doNothing().when(notificationService).sendWebhookMessage(Mockito.any(WebhookPayload.class));

        notificationService.sendWithMdcContext("INFO: SCAN_LOCATION", null, scanLocationConfig,
                Map.of(CloudSyncContext.RUN_ID_KEY, "run-123", CloudSyncContext.LOCATION_KEY,
                        scanLocationConfig.getScanFolder(), CloudSyncContext.PHASE_KEY, "summary"));

        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
        assertNull(MDC.get(CloudSyncContext.LOCATION_KEY));
        assertNull(MDC.get(CloudSyncContext.PHASE_KEY));
    }

    @Test
    void presentAndSendMessageCapturesCurrentMdcForAsyncExecution() {
        ScanLocationConfig scanLocationConfig = new ScanLocationConfig();
        scanLocationConfig.setScanFolder("/var/scanfolder/");
        Mockito.doNothing().when(notificationService).sendWebhookMessage(Mockito.any(WebhookPayload.class));

        try (CloudSyncContext.Scope ignored = CloudSyncContext.open("run-456", scanLocationConfig, "cleanup")) {
            notificationService.presentAndSendMessage("INFO: SCAN_LOCATION", null, scanLocationConfig);
        }

        ArgumentCaptor<WebhookPayload> payloadCaptor = ArgumentCaptor.forClass(WebhookPayload.class);
        Mockito.verify(notificationService).sendWebhookMessage(payloadCaptor.capture());
        assertNotNull(payloadCaptor.getValue());
        assertNull(MDC.get(CloudSyncContext.RUN_ID_KEY));
        assertNull(MDC.get(CloudSyncContext.LOCATION_KEY));
        assertNull(MDC.get(CloudSyncContext.PHASE_KEY));
    }

    @ParameterizedTest
    @MethodSource("messageTemplateTestProvider")
    void testReplaceMessageVariables(String messageTemplate,
            SyncSummaryItem syncSummaryItem,
            ScanLocationConfig scanlocationconfig,
            String expectedResult) {
        String result = notificationService.replaceMessageInTemplate(messageTemplate, syncSummaryItem,
                scanlocationconfig);
        assertEquals(expectedResult, result);
    }

    static Stream<Arguments> messageTemplateTestProvider() {
        ScanLocationConfig scanlocationconfig = new ScanLocationConfig();
        scanlocationconfig.setScanFolder("/var/scanfolder/");
        return Stream.of(
                Arguments.of(":info: Started scan on SCAN_LOCATION", null, scanlocationconfig,
                        ":info: Started scan on " + scanlocationconfig.getScanFolder()),
                Arguments.of("Imported :info: IMPORTED_COUNT for IMPORTED_SIZE \nDeleted :info: DELETED_COUNT for DELETED_SIZE on SCAN_LOCATION",
                        new SyncSummaryItem("2024-08-11", 15, 12025L, 3, 80900L, Instant.now()), scanlocationconfig,
                        "Imported :info: 15 for 12.0 kB \nDeleted :info: 3 for 80.9 kB on "
                                + scanlocationconfig.getScanFolder()),
                Arguments.of("Imported IMPORTED_COUNT for IMPORTED_SIZE, deleted DELETED_COUNT for DELETED_SIZE on SCAN_LOCATION",
                        new SyncSummaryItem("2024-08-11", 15, 12025L, 3, 80900L, Instant.now()), scanlocationconfig,
                        "Imported 15 for 12.0 kB, deleted 3 for 80.9 kB on " + scanlocationconfig.getScanFolder()));
    }
}

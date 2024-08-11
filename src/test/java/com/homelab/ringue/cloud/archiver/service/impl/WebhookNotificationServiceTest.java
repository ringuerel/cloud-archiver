package com.homelab.ringue.cloud.archiver.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.NotificationsConfig;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;

@ExtendWith(MockitoExtension.class)
public class WebhookNotificationServiceTest {

    @Spy
    @InjectMocks
    WebhookNotificationService notificationService;
    @Mock
    ApplicationProperties applicationPropertiesMock;
    @Mock
    NotificationsConfig notificationsConfig;

    @ParameterizedTest
    @MethodSource("messageTemplateTestProvider")
    void testSendMessageFromTemplateShouldInvokeReplaceMessageInTemplate(String messageTemplate, SyncSummaryItem syncSummaryItem,ScanLocationConfig scanlocationconfig){
        Mockito.doNothing().when(notificationService).sendWebhookMessage(Mockito.anyString());
        notificationService.sendMessageFromTemplate(messageTemplate,syncSummaryItem,scanlocationconfig);
        Mockito.verify(notificationService).replaceMessageInTemplate(messageTemplate,syncSummaryItem,scanlocationconfig);
    }

    @ParameterizedTest
    @MethodSource("messageTemplateTestProvider")
    void testReplaceMessageVariables(String messageTemplate, SyncSummaryItem syncSummaryItem,ScanLocationConfig scanlocationconfig, String expectedResult){
        String result = notificationService.replaceMessageInTemplate(messageTemplate, syncSummaryItem, scanlocationconfig);
        assertEquals(expectedResult,result);
    }


    static Stream<Arguments> messageTemplateTestProvider(){
        ScanLocationConfig scanlocationconfig = new ScanLocationConfig();
        scanlocationconfig.setScanFolder("/var/scanfolder/");
        return Stream.of(
            Arguments.of(":info: Started scan on SCAN_LOCATION", null,scanlocationconfig,":info: Started scan on "+scanlocationconfig.getScanFolder()),
            Arguments.of("Imported :info: IMPORTED_COUNT for IMPORTED_SIZE \nDeleted :info: DELETED_COUNT for DELETED_SIZE on SCAN_LOCATION", 
            new SyncSummaryItem("2024-08-11",15,12025L,3,80900L,Instant.now()),scanlocationconfig,
            "Imported :info: 15 for 12.0 kB \nDeleted :info: 3 for 80.9 kB on "+scanlocationconfig.getScanFolder()),
            Arguments.of("Imported IMPORTED_COUNT for IMPORTED_SIZE, deleted DELETED_COUNT for DELETED_SIZE on SCAN_LOCATION", 
            new SyncSummaryItem("2024-08-11",15,12025L,3,80900L,Instant.now()),scanlocationconfig,
            "Imported 15 for 12.0 kB, deleted 3 for 80.9 kB on "+scanlocationconfig.getScanFolder())
        );
    }
}

package com.homelab.ringue.cloud.archiver.service.notification;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Data
@Getter
@Builder
public class WebhookPayload {

    private String username;
    private String content;
    @Singular
    private List<Embed> embeds;

}

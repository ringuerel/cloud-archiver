package com.homelab.ringue.cloud.archiver.service.notification;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
@Builder
public class Field {

    private String name;
    private String value;
    private boolean inline;
}

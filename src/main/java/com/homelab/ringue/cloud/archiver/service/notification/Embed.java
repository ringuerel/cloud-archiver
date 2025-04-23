package com.homelab.ringue.cloud.archiver.service.notification;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Data
@Getter
@Builder
public class Embed {

    private String title;
    private String description;
    private String color;
    @Singular
    private List<Field> fields;
}

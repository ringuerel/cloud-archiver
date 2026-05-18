package com.homelab.ringue.cloud.archiver.domain;

public record PendingDeletionItem(
    FileCatalogItem catalogItem,
    long daysUntilDeletion,
    String scanFolder
) {}

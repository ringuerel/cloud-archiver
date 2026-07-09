package com.homelab.ringue.cloud.archiver.gallery;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.exception.GalleryAccessDeniedException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class ContentSecurityGuard {

    private final ApplicationProperties applicationProperties;
    private final Set<Path> allowedRoots = new LinkedHashSet<>();

    public ContentSecurityGuard(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @PostConstruct
    public void initializeAllowedRoots() {
        allowedRoots.clear();
        addAllowedRoot(applicationProperties.getThumbnailsConfig().getLocalRoot());

        for (ScanLocationConfig scanFolder : Optional.ofNullable(applicationProperties.getScanFolders())
                .orElse(Collections.emptyList())) {
            addAllowedRoot(scanFolder.getScanFolder());
            addAllowedRoot(scanFolder.getThumbnailRoot());
        }
    }

    public Path validateAndResolve(String rawPath) {
        Path resolvedPath = resolvePath(rawPath);
        boolean allowed = allowedRoots.stream().anyMatch(resolvedPath::startsWith);
        if (!allowed) {
            throw new GalleryAccessDeniedException(resolvedPath.toString());
        }
        return resolvedPath;
    }

    Set<Path> allowedRoots() {
        return Collections.unmodifiableSet(allowedRoots);
    }

    private void addAllowedRoot(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return;
        }
        try {
            allowedRoots.add(Paths.get(candidate).toRealPath());
        } catch (IOException e) {
            log.warn("Skipping gallery allowed root because it cannot be resolved: {} ({})", candidate, e.getMessage());
        }
    }

    private Path resolvePath(String rawPath) {
        try {
            return Paths.get(rawPath).toRealPath();
        } catch (IOException e) {
            return Paths.get(rawPath).normalize().toAbsolutePath();
        }
    }
}

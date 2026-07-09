package com.homelab.ringue.cloud.archiver.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;

import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;

public final class CloudSyncContext {

    public static final String RUN_ID_KEY = "syncRunId";
    public static final String LOCATION_KEY = "scanLocation";
    public static final String PHASE_KEY = "syncPhase";

    private CloudSyncContext() {
    }

    public static String newRunId() {
        return UUID.randomUUID().toString();
    }

    public static Scope open(String runId, ScanLocationConfig locationConfig, String phase) {
        setValue(RUN_ID_KEY, runId);
        setValue(LOCATION_KEY, locationConfig == null ? null : locationConfig.getScanFolder());
        setValue(PHASE_KEY, phase);
        return new Scope();
    }

    public static void updatePhase(String phase) {
        setValue(PHASE_KEY, phase);
    }

    public static void clear() {
        MDC.remove(PHASE_KEY);
        MDC.remove(LOCATION_KEY);
        MDC.remove(RUN_ID_KEY);
    }

    public static void put(String key, String value) {
        setValue(key, value);
    }

    public static Optional<String> get(String key) {
        return Optional.ofNullable(MDC.get(key));
    }

    public static Scope restore(Map<String, String> contextMap) {
        return new Scope(MDC.getCopyOfContextMap(), false, contextMap);
    }

    public static Scope preserve() {
        return new Scope(MDC.getCopyOfContextMap(), true, null);
    }

    public static Optional<String> getRunId() {
        return Optional.ofNullable(MDC.get(RUN_ID_KEY));
    }

    private static void setValue(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }

    public static final class Scope implements AutoCloseable {
        private final Map<String, String> previousContext;
        private final boolean restorePreviousContext;
        private boolean closed;

        private Scope() {
            this(MDC.getCopyOfContextMap(), false, null);
        }

        private Scope(Map<String, String> previousContext, boolean restorePreviousContext, Map<String, String> nextContext) {
            this.previousContext = previousContext;
            this.restorePreviousContext = restorePreviousContext;
            if (nextContext == null || nextContext.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(nextContext);
            }
        }

        @Override
        public void close() {
            if (!closed) {
                if (restorePreviousContext) {
                    if (previousContext == null || previousContext.isEmpty()) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previousContext);
                    }
                } else {
                    clear();
                }
                closed = true;
            }
        }
    }
}

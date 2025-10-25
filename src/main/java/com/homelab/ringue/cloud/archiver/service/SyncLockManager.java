package com.homelab.ringue.cloud.archiver.service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SyncLockManager {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean isLocked = false;
    private Instant lockAcquisitionTime;

    public boolean acquireLock(long timeoutSeconds) {
        lock.lock();
        try {
            if (isLocked) {
                // Check for timeout
                if (lockAcquisitionTime != null && Instant.now().isAfter(lockAcquisitionTime.plusSeconds(timeoutSeconds))) {
                    log.warn("Sync lock was found to be stale (acquired at {}), forcing release.", lockAcquisitionTime);
                    releaseLock(); // Force release stale lock
                } else {
                    log.info("Sync process is already running. Skipping new sync request.");
                    return false;
                }
            }
            isLocked = true;
            lockAcquisitionTime = Instant.now();
            log.info("Sync lock acquired at {}.", lockAcquisitionTime);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void releaseLock() {
        lock.lock();
        try {
            if (isLocked) {
                isLocked = false;
                lockAcquisitionTime = null;
                log.info("Sync lock released.");
                condition.signalAll(); // Notify any waiting threads
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isLocked() {
        lock.lock();
        try {
            return isLocked;
        } finally {
            lock.unlock();
        }
    }
}

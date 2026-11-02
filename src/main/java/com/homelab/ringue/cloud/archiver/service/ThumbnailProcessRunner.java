package com.homelab.ringue.cloud.archiver.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public interface ThumbnailProcessRunner {

    ProcessResult run(List<String> command, Duration timeout) throws IOException, InterruptedException;

    record ProcessResult(int exitCode, String output) {}
}

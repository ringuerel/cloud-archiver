package com.homelab.ringue.cloud.archiver.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.homelab.ringue.cloud.archiver.service.ThumbnailProcessRunner;

@Component
public class DefaultThumbnailProcessRunner implements ThumbnailProcessRunner {

    @Override
    public ProcessResult run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        CompletableFuture<String> outputReader = CompletableFuture.supplyAsync(() -> readOutput(process));

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
            return new ProcessResult(-1, "Command timed out after " + timeout.toSeconds() + " seconds: " + output(outputReader));
        }

        return new ProcessResult(process.exitValue(), output(outputReader));
    }

    private String readOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private String output(CompletableFuture<String> outputReader) throws InterruptedException {
        try {
            return outputReader.get();
        } catch (ExecutionException e) {
            return e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
        }
    }
}

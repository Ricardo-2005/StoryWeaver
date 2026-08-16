package com.storyweaver.usage.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UsageAttributionContext {
    private final ThreadLocal<UUID> reconstructionJob = new ThreadLocal<>();

    public Scope reconstruction(UUID jobId) {
        UUID previous = reconstructionJob.get();
        reconstructionJob.set(jobId);
        return () -> {
            if (previous == null) reconstructionJob.remove();
            else reconstructionJob.set(previous);
        };
    }

    public UUID currentReconstructionJob() {
        return reconstructionJob.get();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}

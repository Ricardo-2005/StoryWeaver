package com.storyweaver.llm.application;

import com.storyweaver.shared.error.ConflictException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WriterConcurrencyGuard {
    private final Set<UUID> activeProjects = new HashSet<>();
    private final Set<UUID> activeUsers = new HashSet<>();

    public synchronized Lease acquire(UUID projectId, UUID userId) {
        if (activeProjects.contains(projectId)) {
            throw new ConflictException("project_writer_active", "The project already has an active Writer");
        }
        if (activeUsers.contains(userId)) {
            throw new ConflictException("user_writer_active", "The user already has an active Writer");
        }
        activeProjects.add(projectId);
        activeUsers.add(userId);
        return new Lease(projectId, userId);
    }

    public final class Lease implements AutoCloseable {
        private final UUID projectId;
        private final UUID userId;
        private boolean closed;

        private Lease(UUID projectId, UUID userId) {
            this.projectId = projectId;
            this.userId = userId;
        }

        @Override
        public void close() {
            synchronized (WriterConcurrencyGuard.this) {
                if (closed) return;
                activeProjects.remove(projectId);
                activeUsers.remove(userId);
                closed = true;
            }
        }
    }
}

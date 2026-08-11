package com.storyweaver.workflow.domain;

import com.storyweaver.shared.error.ConflictException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkflowStateMachine {
    private static final Map<WorkflowStatus, Set<WorkflowStatus>> TRANSITIONS = transitions();

    public void requireTransition(WorkflowStatus current, WorkflowStatus next) {
        if (current == next) return;
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new ConflictException(
                    "invalid_workflow_transition", "Workflow cannot transition from " + current + " to " + next);
        }
    }

    private static Map<WorkflowStatus, Set<WorkflowStatus>> transitions() {
        Map<WorkflowStatus, Set<WorkflowStatus>> values = new EnumMap<>(WorkflowStatus.class);
        values.put(WorkflowStatus.CREATED, allowed(WorkflowStatus.PREFLIGHT));
        values.put(WorkflowStatus.PREFLIGHT, allowed(WorkflowStatus.CONTEXT_READY, WorkflowStatus.BLOCKED));
        values.put(WorkflowStatus.CONTEXT_READY, allowed(WorkflowStatus.PLANNING));
        values.put(WorkflowStatus.PLANNING, allowed(WorkflowStatus.PLAN_READY));
        values.put(WorkflowStatus.PLAN_READY, allowed(WorkflowStatus.WRITING));
        values.put(WorkflowStatus.WRITING, allowed(WorkflowStatus.TEXT_READY));
        values.put(WorkflowStatus.TEXT_READY, allowed(WorkflowStatus.EXTRACTING));
        values.put(WorkflowStatus.EXTRACTING, allowed(WorkflowStatus.VALIDATING));
        values.put(WorkflowStatus.VALIDATING, allowed(WorkflowStatus.REVIEWING));
        values.put(WorkflowStatus.REVIEWING, allowed(WorkflowStatus.WAITING_APPROVAL));
        values.put(
                WorkflowStatus.WAITING_APPROVAL,
                allowed(
                        WorkflowStatus.REVISION_REQUIRED,
                        WorkflowStatus.TEXT_READY,
                        WorkflowStatus.COMMITTING,
                        WorkflowStatus.CANCELLED,
                        WorkflowStatus.ROLLED_BACK));
        values.put(WorkflowStatus.REVISION_REQUIRED, allowed(WorkflowStatus.TEXT_READY, WorkflowStatus.CANCELLED));
        values.put(WorkflowStatus.COMMITTING, allowed(WorkflowStatus.COMPLETED, WorkflowStatus.ROLLED_BACK));
        values.replaceAll((status, targets) -> {
            if (status.isExecuting()) {
                targets.add(WorkflowStatus.FAILED);
                targets.add(WorkflowStatus.CANCELLED);
            }
            return targets;
        });
        return Map.copyOf(values);
    }

    private static EnumSet<WorkflowStatus> allowed(WorkflowStatus first, WorkflowStatus... rest) {
        return EnumSet.of(first, rest);
    }
}

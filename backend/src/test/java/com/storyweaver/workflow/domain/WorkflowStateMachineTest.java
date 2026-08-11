package com.storyweaver.workflow.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyweaver.shared.error.ConflictException;
import org.junit.jupiter.api.Test;

class WorkflowStateMachineTest {
    private final WorkflowStateMachine machine = new WorkflowStateMachine();

    @Test
    void acceptsPhaseSixReviewCommitAndRevisionPathsAndRejectsSkippingSteps() {
        machine.requireTransition(WorkflowStatus.CREATED, WorkflowStatus.PREFLIGHT);
        machine.requireTransition(WorkflowStatus.PREFLIGHT, WorkflowStatus.CONTEXT_READY);
        machine.requireTransition(WorkflowStatus.CONTEXT_READY, WorkflowStatus.PLANNING);
        machine.requireTransition(WorkflowStatus.PLANNING, WorkflowStatus.PLAN_READY);
        machine.requireTransition(WorkflowStatus.PLAN_READY, WorkflowStatus.WRITING);
        machine.requireTransition(WorkflowStatus.WRITING, WorkflowStatus.TEXT_READY);
        machine.requireTransition(WorkflowStatus.TEXT_READY, WorkflowStatus.EXTRACTING);
        machine.requireTransition(WorkflowStatus.EXTRACTING, WorkflowStatus.VALIDATING);
        machine.requireTransition(WorkflowStatus.VALIDATING, WorkflowStatus.REVIEWING);
        machine.requireTransition(WorkflowStatus.REVIEWING, WorkflowStatus.WAITING_APPROVAL);
        machine.requireTransition(WorkflowStatus.WAITING_APPROVAL, WorkflowStatus.COMMITTING);
        machine.requireTransition(WorkflowStatus.COMMITTING, WorkflowStatus.COMPLETED);
        machine.requireTransition(WorkflowStatus.WAITING_APPROVAL, WorkflowStatus.REVISION_REQUIRED);
        machine.requireTransition(WorkflowStatus.REVISION_REQUIRED, WorkflowStatus.TEXT_READY);

        assertThatThrownBy(() -> machine.requireTransition(WorkflowStatus.CREATED, WorkflowStatus.WRITING))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> machine.requireTransition(WorkflowStatus.WAITING_APPROVAL, WorkflowStatus.REVIEWING))
                .isInstanceOf(ConflictException.class);
    }
}

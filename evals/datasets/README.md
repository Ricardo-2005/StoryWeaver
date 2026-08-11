# Dataset v1

- `rag/retrieval_cases.jsonl`: 50 human-authored retrieval questions with reviewed relevant and required logical IDs.
- `consistency/consistency_cases.jsonl`: 100 original cases, balanced 50 clean / 50 conflict across five production validator categories.
- `workflow/workflow_cases.jsonl`: deterministic scenarios aligned with the implemented `WorkflowStatus` state machine.
- `mcp/mcp_cases.jsonl`: calls against tools discovered from `StoryMcpCapabilities` annotations.

Ground-truth source is `HUMAN`. Fixture prose is synthetic and does not contain a user manuscript. Dataset changes require an entry in `CHANGELOG.md`; failures are retained.

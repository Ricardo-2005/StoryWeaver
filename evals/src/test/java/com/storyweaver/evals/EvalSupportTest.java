package com.storyweaver.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalSupportTest {
    @TempDir
    Path temp;

    @Test
    void parsesVersionedDatasetAndRejectsMismatch() throws Exception {
        Path file = temp.resolve("cases.jsonl");
        String value = "{\"datasetVersion\":\"v1\",\"caseId\":\"x\",\"category\":\"RAG\","
                + "\"description\":\"x\",\"fixtureProject\":\"p\",\"input\":{},\"expected\":{},"
                + "\"tags\":[],\"createdBy\":\"HUMAN\",\"version\":1}";
        Files.writeString(file, value, StandardCharsets.UTF_8);
        assertThat(EvalSupport.readJsonl(file, "v1")).hasSize(1);
        assertThatThrownBy(() -> EvalSupport.readJsonl(file, "v2")).hasMessageContaining("datasetVersion mismatch");
    }

    @Test
    void redactsSecretsFromFailureArtifacts() {
        assertThat(EvalSupport.redact("Authorization: Bearer abcdefghijklmnop"))
                .doesNotContain("abcdefghijklmnop")
                .contains("[REDACTED]");
        assertThat(EvalSupport.redact("api_key=very-secret-value"))
                .doesNotContain("very-secret-value")
                .contains("[REDACTED]");
    }
}

package com.storyweaver;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.llm.application.AgentContracts.ChapterPlan;
import com.storyweaver.llm.application.AgentContracts.ExtractionResult;
import com.storyweaver.llm.application.AgentContracts.ReviewResult;
import com.storyweaver.llm.application.AgentContracts.ScenePlan;
import com.storyweaver.llm.application.EmbeddingGateway;
import com.storyweaver.llm.application.ExtractorGateway;
import com.storyweaver.llm.application.PlannerGateway;
import com.storyweaver.llm.application.ReviewerGateway;
import com.storyweaver.llm.application.WorkflowWriterGateway;
import com.storyweaver.llm.application.WorkflowWriterGateway.WriterResult;
import com.storyweaver.workflow.application.WorkflowRecoveryWorker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {StoryWeaverApplication.class, Phase5ApiIT.DeterministicWorkflowConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase5ApiIT {
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-bookworm").asCompatibleSubstituteFor("postgres");
    private static final Pattern SSE_ID = Pattern.compile("(?m)^id:(\\d+)\\s*$");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("storyweaver.security.jwt.secret", () -> "phase-five-jwt-secret-at-least-32-bytes");
        registry.add("storyweaver.deepseek.api-key", () -> "phase-five-contract-test-key");
        registry.add("storyweaver.workflow.stale-run-timeout", () -> "1s");
        registry.add("storyweaver.workflow.recovery-interval", () -> "1h");
        registry.add("storyweaver.workflow.event-poll-interval", () -> "20ms");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WorkflowRecoveryWorker recoveryWorker;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void producesRuntimeDraftPersistsReplayableEventsAndHonorsIdempotencyAndIsolation() throws Exception {
        Identity owner = identity("phase5-owner", "phase5-owner@example.com");
        Identity other = identity("phase5-other", "phase5-other@example.com");
        String projectId = createProject(owner.token(), "Workflow Project");
        String characterId = createCharacter(owner.token(), projectId, "路明非");
        String chapterId = createChapter(owner.token(), projectId, 1, "进入青铜城", "路明非抵达三峡并寻找青铜城水下入口");
        Map<String, Object> body = Map.of("viewpointCharacterId", characterId, "instruction", "完成原创测试章，结尾留下龙文机关的线索");

        JsonNode started = json(request(
                "POST",
                "/api/chapters/" + chapterId + "/workflows",
                owner.token(),
                body,
                202,
                "phase5-idempotent-001"));
        String runId = started.get("id").asString();
        JsonNode repeated = json(request(
                "POST",
                "/api/chapters/" + chapterId + "/workflows",
                owner.token(),
                body,
                202,
                "phase5-idempotent-001"));
        assertThat(repeated.get("id").asString()).isEqualTo(runId);

        request("POST", "/api/chapters/" + chapterId + "/workflows", owner.token(), body, 409, "phase5-another-002");

        JsonNode finished = awaitStatus(owner.token(), runId, "WAITING_APPROVAL", Duration.ofSeconds(10));
        assertThat(finished.get("draftContent").asString()).contains("三峡", "龙文机关");
        assertThat(finished.get("plan").get("chapterTitle").asString()).isEqualTo("青铜城入口调查");
        assertThat(finished.get("extraction").get("summary").asString()).isNotBlank();
        assertThat(finished.get("contextPacket").get("stale").asBoolean()).isFalse();
        assertThat(finished.get("steps")).hasSize(7);
        assertThat(jdbc.queryForObject(
                        "select count(*) from chapter_version where chapter_id=?::uuid", Integer.class, chapterId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from workflow_event where workflow_run_id=?::uuid", Integer.class, runId))
                .isGreaterThan(8);

        String replay = sse(owner.token(), runId, null);
        assertThat(replay).contains("event:text.delta", "event:text.completed", "event:workflow.step");
        Matcher matcher = SSE_ID.matcher(replay);
        assertThat(matcher.find()).isTrue();
        String firstId = matcher.group(1);
        String reconnected = sse(owner.token(), runId, firstId);
        assertThat(reconnected).doesNotContain("id:" + firstId + "\n").contains("event:workflow.step");

        request("GET", "/api/workflows/" + runId, other.token(), null, 404, null);
        jdbc.update(
                "update context_packet set expires_at=now() - interval '1 second' where workflow_run_id=?::uuid",
                runId);
        JsonNode stale = json(request("GET", "/api/workflows/" + runId, owner.token(), null, 200, null));
        assertThat(stale.get("contextPacket").get("stale").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select to_regclass('mcp_audit_log')", String.class))
                .isEqualTo("mcp_audit_log");
    }

    @Test
    void cancellationStopsRuntimeWritingWithoutCreatingAFormalVersion() throws Exception {
        Identity owner = identity("phase5-cancel", "phase5-cancel@example.com");
        String projectId = createProject(owner.token(), "Cancellation Project");
        String characterId = createCharacter(owner.token(), projectId, "楚子航");
        String chapterId = createChapter(owner.token(), projectId, 1, "中断", "楚子航调查异常龙文机关");
        JsonNode started = json(request(
                "POST",
                "/api/chapters/" + chapterId + "/workflows",
                owner.token(),
                Map.of("viewpointCharacterId", characterId, "instruction", "SLOW 生成可取消草稿"),
                202,
                "phase5-cancel-001"));
        String runId = started.get("id").asString();
        awaitStatus(owner.token(), runId, "WRITING", Duration.ofSeconds(10));
        JsonNode cancelled =
                json(request("POST", "/api/workflows/" + runId + "/cancel", owner.token(), null, 200, null));
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
        Thread.sleep(300);
        assertThat(jdbc.queryForObject(
                        "select count(*) from chapter_version where chapter_id=?::uuid", Integer.class, chapterId))
                .isZero();
    }

    @Test
    void recoveryWorkerResetsInterruptedDraftAndResumesFromPersistedWritingState() throws Exception {
        Identity owner = identity("phase5-recovery", "phase5-recovery@example.com");
        String projectId = createProject(owner.token(), "Recovery Project");
        String characterId = createCharacter(owner.token(), projectId, "诺诺");
        String chapterId = createChapter(owner.token(), projectId, 1, "恢复", "诺诺复核青铜城入口资料");
        UUID runId = UUID.randomUUID();
        jdbc.update(
                """
                insert into workflow_run(
                    id, project_id, chapter_id, user_id, viewpoint_character_id, idempotency_key,
                    instruction, status, plan_json, draft_content, heartbeat_at, started_at, created_at, updated_at)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, 'phase5-recovery-001',
                    '恢复写作', 'WRITING', '{"chapterTitle":"青铜城恢复计划"}'::jsonb, 'PARTIAL_OLD',
                    now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour')
                """,
                runId,
                projectId,
                chapterId,
                owner.userId(),
                characterId);
        jdbc.update(
                """
                insert into context_packet(
                    id, project_id, chapter_id, workflow_run_id, created_by, context_data,
                    worldbook_report, memory_report, skill_snapshot, token_estimate,
                    estimated_cost, expires_at, created_at)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid,
                    '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, 10, 0, now() + interval '1 hour', now())
                """,
                UUID.randomUUID(),
                projectId,
                chapterId,
                runId,
                owner.userId());

        recoveryWorker.recoverStaleRuns();
        JsonNode recovered = awaitStatus(owner.token(), runId.toString(), "WAITING_APPROVAL", Duration.ofSeconds(10));
        assertThat(recovered.get("recoveryCount").asInt()).isEqualTo(1);
        assertThat(recovered.get("draftContent").asString())
                .doesNotContain("PARTIAL_OLD")
                .contains("青铜城");
        assertThat(jdbc.queryForObject(
                        "select attempt from workflow_step where workflow_run_id=?::uuid and step_name='WRITING'",
                        Integer.class,
                        runId))
                .isEqualTo(1);
    }

    private Identity identity(String username, String email) throws Exception {
        JsonNode response = json(request(
                "POST",
                "/api/auth/register",
                null,
                Map.of("username", username, "email", email, "password", "Phase5Password!"),
                201,
                null));
        return new Identity(
                response.get("accessToken").asString(),
                response.get("user").get("id").asString());
    }

    private String createProject(String token, String name) throws Exception {
        return json(request(
                        "POST",
                        "/api/projects",
                        token,
                        Map.of(
                                "name",
                                name,
                                "genre",
                                "FANTASY",
                                "targetAudience",
                                "GENERAL",
                                "narrativePerspective",
                                "THIRD_PERSON",
                                "lengthType",
                                "LONG_NOVEL",
                                "premise",
                                "调查青铜城的秘密并保持人物知识边界。",
                                "worldRules",
                                List.of(),
                                "authorIntent",
                                "保持人物知识、炼金武器归属与世界规则一致",
                                "currentFocus",
                                "推进青铜城调查"),
                        201,
                        null))
                .get("id")
                .asString();
    }

    private String createCharacter(String token, String projectId, String name) throws Exception {
        return json(request(
                        "POST",
                        "/api/projects/" + projectId + "/characters",
                        token,
                        Map.of("name", name, "role", "PROTAGONIST"),
                        201,
                        null))
                .get("id")
                .asString();
    }

    private String createChapter(String token, String projectId, int no, String title, String outline)
            throws Exception {
        return json(request(
                        "POST",
                        "/api/projects/" + projectId + "/chapters",
                        token,
                        Map.of("chapterNo", no, "title", title, "outline", outline),
                        201,
                        null))
                .get("id")
                .asString();
    }

    private JsonNode awaitStatus(String token, String runId, String expected, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        JsonNode response = null;
        while (System.nanoTime() < deadline) {
            response = json(request("GET", "/api/workflows/" + runId, token, null, 200, null));
            if (expected.equals(response.get("status").asString())) return response;
            if (List.of("FAILED", "BLOCKED", "CANCELLED")
                    .contains(response.get("status").asString())) break;
            Thread.sleep(30);
        }
        throw new AssertionError("Expected " + expected + " but was " + response);
    }

    private String sse(String token, String runId, String lastEventId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/workflows/" + runId + "/events"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "text/event-stream")
                .GET();
        if (lastEventId != null) builder.header("Last-Event-ID", lastEventId);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private HttpResponse<String> request(
            String method, String path, String token, Object body, int expectedStatus, String idempotencyKey)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response;
    }

    private JsonNode json(HttpResponse<String> response) {
        return objectMapper.readTree(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private record Identity(String token, String userId) {}

    @TestConfiguration
    static class DeterministicWorkflowConfiguration {
        @Bean
        @Primary
        EmbeddingGateway deterministicEmbedding() {
            return text -> {
                float[] vector = new float[512];
                vector[Math.floorMod(text.hashCode(), vector.length)] = 1.0f;
                return EmbeddingGateway.EmbeddingResult.available(vector, "phase5-test-embedding");
            };
        }

        @Bean
        @Primary
        PlannerGateway deterministicPlanner() {
            return (projectId, userId, input) -> new ChapterPlan(
                    "青铜城入口调查",
                    "发现入口并记录龙文线索",
                    null,
                    List.of(new ScenePlan("三峡水域", "确认入口", "路明非在水下勘测点核对入口", List.of(), List.of())),
                    List.of("龙文机关"),
                    List.of(),
                    "龙文机关出现新的测试信号");
        }

        @Bean
        @Primary
        WorkflowWriterGateway deterministicWriter() {
            return (projectId, userId, input, chunks) -> {
                chunks.accept("路明非抵达三峡任务现场，在水下勘测点完成身份校验。");
                try {
                    Thread.sleep(input.instruction().contains("SLOW") ? 700 : 300);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                chunks.accept("离开勘测点时，他发现青铜城入口附近的龙文机关发出微弱信号。");
                return new WriterResult("test-request", "test-writer", "stop", 100, 80, 10, 90, 20);
            };
        }

        @Bean
        @Primary
        ExtractorGateway deterministicExtractor() {
            return (projectId, userId, input) -> new ExtractionResult(
                    "路明非抵达三峡并发现龙文机关线索",
                    List.of("调查组确认青铜城水下入口"),
                    List.of("龙文机关需要进一步解析"),
                    List.of(),
                    List.of(),
                    List.of());
        }

        @Bean
        @Primary
        ReviewerGateway deterministicReviewer() {
            return (projectId, userId, input) -> new ReviewResult(List.of(), "未发现阻止审批的问题");
        }
    }
}

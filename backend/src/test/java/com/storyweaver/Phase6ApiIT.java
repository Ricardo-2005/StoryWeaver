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
import com.storyweaver.workflow.application.AtomicCommitFaultInjector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
        classes = {StoryWeaverApplication.class, Phase6ApiIT.DeterministicPhase6Configuration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase6ApiIT {
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-bookworm").asCompatibleSubstituteFor("postgres");

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
        registry.add("storyweaver.security.jwt.secret", () -> "phase-six-jwt-secret-at-least-32-bytes");
        registry.add("storyweaver.deepseek.api-key", () -> "phase-six-contract-test-key");
        registry.add("storyweaver.workflow.recovery-interval", () -> "1h");
        registry.add("storyweaver.workflow.event-poll-interval", () -> "20ms");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void atomicallyCommitsAcceptedStateAndMakesItVisibleToTheNextChapter() throws Exception {
        Identity owner = identity("phase6-commit", "phase6-commit@example.com");
        String projectId = createProject(owner.token(), "Atomic Project");
        String characterId = createCharacter(owner.token(), projectId, "路明非", "ALIVE");
        String chapterId = createChapter(owner.token(), projectId, 1, "青铜城入口", "路明非抵达三峡并发现青铜城水下入口");
        JsonNode ready = startAndAwait(owner.token(), chapterId, characterId, "生成第一章并提交状态", "phase6-commit-001");

        assertThat(ready.get("candidateFacts")).hasSize(1);
        assertThat(ready.get("steps")).hasSize(7);
        long workflowVersion = ready.get("version").asLong();
        long stateVersion = json(request("GET", "/api/characters/" + characterId + "/state", owner.token(), null, 200))
                .get("version")
                .asLong();
        Map<String, Object> approval = Map.of(
                "expectedVersion",
                workflowVersion,
                "changeSummary",
                "Phase 6 atomic commit",
                "acceptedFactIndexes",
                List.of(0),
                "characterStateChanges",
                List.of(Map.of(
                        "characterId",
                        characterId,
                        "lifeStatus",
                        "ALIVE",
                        "currentLocation",
                        "三峡任务现场",
                        "physicalCondition",
                        "轻微疲惫",
                        "expectedVersion",
                        stateVersion,
                        "evidence",
                        "路明非从卡塞尔学院出发并抵达三峡任务现场")),
                "itemChanges",
                List.of(Map.of(
                        "itemKey",
                        "seven-sins-sword-case",
                        "itemName",
                        "完整七宗罪剑匣",
                        "toOwnerCharacterId",
                        characterId,
                        "status",
                        "ACTIVE",
                        "evidence",
                        "路明非按任务交接记录接收完整七宗罪剑匣")),
                "timelineEvents",
                List.of(Map.of(
                        "participantIds",
                        List.of(characterId),
                        "knownByIds",
                        List.of(characterId),
                        "location",
                        "青铜城水下入口",
                        "storyTime",
                        "2026-08-02",
                        "action",
                        "发现青铜城水下入口",
                        "result",
                        "调查组获得进入青铜城的前置证据",
                        "importance",
                        0.8,
                        "evidence",
                        "路明非记录入口位置和发现时间")),
                "knowledgeChanges",
                List.of(Map.of(
                        "characterId",
                        characterId,
                        "factKey",
                        "bronze-fire-king-identity",
                        "content",
                        "青铜与火之王身份仍需现场证据确认",
                        "certainty",
                        "CONFIRMED",
                        "evidence",
                        "路明非在青铜城入口发现龙文机关证据后确认该身份")));
        JsonNode committed = json(request(
                "POST", "/api/workflows/" + ready.get("id").asString() + "/approve", owner.token(), approval, 200));

        assertThat(committed.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(committed.get("committedVersionNo").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from chapter_version where chapter_id=?::uuid", Integer.class, chapterId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from story_fact where workflow_run_id=?::uuid and status='ACCEPTED'",
                        Integer.class,
                        ready.get("id").asString()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from item_ownership where project_id=?::uuid and item_key='seven-sins-sword-case'",
                        Integer.class,
                        projectId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from character_knowledge where project_id=?::uuid and fact_key='bronze-fire-king-identity'",
                        Integer.class,
                        projectId))
                .isEqualTo(1);
        assertThat(json(request(
                                "GET",
                                "/api/projects/" + projectId + "/story-facts?status=ACCEPTED",
                                owner.token(),
                                null,
                                200))
                        .size())
                .isEqualTo(1);
        assertThat(json(request("GET", "/api/projects/" + projectId + "/item-ownership", owner.token(), null, 200))
                        .size())
                .isEqualTo(1);
        assertThat(json(request("GET", "/api/characters/" + characterId + "/knowledge", owner.token(), null, 200))
                        .size())
                .isEqualTo(1);

        String chapter2 = createChapter(owner.token(), projectId, 2, "龙文机关", "路明非与楚子航继续调查青铜城龙文机关");
        JsonNode next = startAndAwait(owner.token(), chapter2, characterId, "生成第二章", "phase6-next-002");
        String packet = jdbc.queryForObject(
                "select context_data::text from context_packet where workflow_run_id=?::uuid",
                String.class,
                next.get("id").asString());
        assertThat(packet).contains("seven-sins-sword-case", "bronze-fire-king-identity", "三峡任务现场");
        assertThat(jdbc.queryForObject("select to_regclass('mcp_audit_log')", String.class))
                .isEqualTo("mcp_audit_log");
    }

    @Test
    void blockerPreventsApprovalAndLeavesChapterUncommitted() throws Exception {
        Identity owner = identity("phase6-blocker", "phase6-blocker@example.com");
        String projectId = createProject(owner.token(), "Blocker Project");
        String characterId = createCharacter(owner.token(), projectId, "路明非", "DEAD");
        String chapterId = createChapter(owner.token(), projectId, 1, "人物状态矛盾", "路明非正常走入青铜城门廊");
        JsonNode ready = startAndAwait(owner.token(), chapterId, characterId, "检测死亡人物冲突", "phase6-block-001");

        assertThat(ready.get("reviewIssues").values()).anySatisfy(issue -> {
            assertThat(issue.get("category").asString()).isEqualTo("CHARACTER_STATE");
            assertThat(issue.get("severity").asString()).isEqualTo("BLOCKER");
        });
        request(
                "POST",
                "/api/workflows/" + ready.get("id").asString() + "/approve",
                owner.token(),
                emptyApproval(ready.get("version").asLong()),
                409);
        assertThat(jdbc.queryForObject(
                        "select count(*) from chapter_version where chapter_id=?::uuid", Integer.class, chapterId))
                .isZero();
        assertThat(json(request("GET", "/api/workflows/" + ready.get("id").asString(), owner.token(), null, 200))
                        .get("status")
                        .asString())
                .isEqualTo("WAITING_APPROVAL");
    }

    @Test
    void editedDraftIsReextractedAndRereviewedBeforeApproval() throws Exception {
        Identity owner = identity("phase6-revision", "phase6-revision@example.com");
        String projectId = createProject(owner.token(), "Revision Project");
        String characterId = createCharacter(owner.token(), projectId, "诺诺", "ALIVE");
        String chapterId = createChapter(owner.token(), projectId, 1, "修订", "诺诺复核龙文机关记录");
        JsonNode first = startAndAwait(owner.token(), chapterId, characterId, "生成待修订草稿", "phase6-revision-001");

        json(request(
                "POST",
                "/api/workflows/" + first.get("id").asString() + "/reextract",
                owner.token(),
                Map.of("revisedDraft", "修订后：诺诺把龙文机关结论标记为待确认，并补充信息传播记录。"),
                200));
        JsonNode revised = awaitStatus(owner.token(), first.get("id").asString(), "WAITING_APPROVAL");
        assertThat(revised.get("revisionCount").asInt()).isEqualTo(1);
        assertThat(revised.get("extraction").get("summary").asString()).contains("修订后");
        assertThat(revised.get("candidateFacts").get(0).get("content").asString())
                .contains("修订事实");
        assertThat(revised.get("steps").values())
                .filteredOn(step -> step.get("stepName").asString().equals("EXTRACTING"))
                .singleElement()
                .satisfies(step -> assertThat(step.get("attempt").asInt()).isEqualTo(2));

        Map<String, Object> approval = emptyApproval(revised.get("version").asLong());
        @SuppressWarnings("unchecked")
        Map<String, Object> mutable = new java.util.LinkedHashMap<>(approval);
        mutable.put("acceptedFactIndexes", List.of(0));
        JsonNode committed = json(request(
                "POST", "/api/workflows/" + revised.get("id").asString() + "/approve", owner.token(), mutable, 200));
        assertThat(committed.get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    void transactionFailureRollsBackChapterAndStoryState() throws Exception {
        Identity owner = identity("phase6-rollback", "phase6-rollback@example.com");
        String projectId = createProject(owner.token(), "Rollback Project");
        String characterId = createCharacter(owner.token(), projectId, "恺撒", "ALIVE");
        String chapterId = createChapter(owner.token(), projectId, 1, "回滚", "恺撒核对学生会支援记录");
        JsonNode ready =
                startAndAwait(owner.token(), chapterId, characterId, "ROLLBACK_TEST 生成草稿", "phase6-rollback-001");

        request(
                "POST",
                "/api/workflows/" + ready.get("id").asString() + "/approve",
                owner.token(),
                emptyApproval(ready.get("version").asLong()),
                500);
        JsonNode rolledBack =
                json(request("GET", "/api/workflows/" + ready.get("id").asString(), owner.token(), null, 200));
        assertThat(rolledBack.get("status").asString()).isEqualTo("ROLLED_BACK");
        assertThat(rolledBack.get("failureCode").asString()).isEqualTo("atomic_commit_rolled_back");
        assertThat(jdbc.queryForObject(
                        "select count(*) from chapter_version where chapter_id=?::uuid", Integer.class, chapterId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select current_version_no from chapter where id=?::uuid", Integer.class, chapterId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from story_fact where workflow_run_id=?::uuid and status='ACCEPTED'",
                        Integer.class,
                        ready.get("id").asString()))
                .isZero();
    }

    private JsonNode startAndAwait(String token, String chapterId, String characterId, String instruction, String key)
            throws Exception {
        JsonNode started = json(request(
                "POST",
                "/api/chapters/" + chapterId + "/workflows",
                token,
                Map.of("viewpointCharacterId", characterId, "instruction", instruction),
                202,
                key));
        return awaitStatus(token, started.get("id").asString(), "WAITING_APPROVAL");
    }

    private JsonNode awaitStatus(String token, String runId, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        JsonNode current = null;
        while (System.nanoTime() < deadline) {
            current = json(request("GET", "/api/workflows/" + runId, token, null, 200));
            if (expected.equals(current.get("status").asString())) return current;
            if (List.of("FAILED", "BLOCKED", "CANCELLED", "ROLLED_BACK")
                    .contains(current.get("status").asString())) break;
            Thread.sleep(30);
        }
        throw new AssertionError("Expected " + expected + " but was " + current);
    }

    private Map<String, Object> emptyApproval(long expectedVersion) {
        return Map.of(
                "expectedVersion",
                expectedVersion,
                "acceptedFactIndexes",
                List.of(),
                "characterStateChanges",
                List.of(),
                "itemChanges",
                List.of(),
                "timelineEvents",
                List.of(),
                "knowledgeChanges",
                List.of());
    }

    private Identity identity(String username, String email) throws Exception {
        JsonNode response = json(request(
                "POST",
                "/api/auth/register",
                null,
                Map.of("username", username, "email", email, "password", "phase-six-password"),
                201));
        return new Identity(response.get("accessToken").asString());
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
                                "在连续的世界状态中推进人物选择。",
                                "worldRules",
                                List.of(),
                                "authorIntent",
                                "保持人物和世界状态连续"),
                        201))
                .get("id")
                .asString();
    }

    private String createCharacter(String token, String projectId, String name, String lifeStatus) throws Exception {
        return json(request(
                        "POST",
                        "/api/projects/" + projectId + "/characters",
                        token,
                        Map.of(
                                "name",
                                name,
                                "role",
                                "主角",
                                "state",
                                Map.of("lifeStatus", lifeStatus, "currentLocation", "卡塞尔学院")),
                        201))
                .get("id")
                .asString();
    }

    private String createChapter(String token, String projectId, int chapterNo, String title, String outline)
            throws Exception {
        return json(request(
                        "POST",
                        "/api/projects/" + projectId + "/chapters",
                        token,
                        Map.of("chapterNo", chapterNo, "title", title, "outline", outline),
                        201))
                .get("id")
                .asString();
    }

    private HttpResponse<String> request(String method, String path, String token, Object body, int expectedStatus)
            throws Exception {
        return request(method, path, token, body, expectedStatus, null);
    }

    private HttpResponse<String> request(
            String method, String path, String token, Object body, int expectedStatus, String idempotencyKey)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private JsonNode json(HttpResponse<String> response) {
        return objectMapper.readTree(response.body());
    }

    private record Identity(String token) {}

    @TestConfiguration
    static class DeterministicPhase6Configuration {
        @Bean
        @Primary
        EmbeddingGateway deterministicEmbedding() {
            return text -> {
                float[] vector = new float[512];
                vector[Math.floorMod(text.hashCode(), vector.length)] = 1.0f;
                return EmbeddingGateway.EmbeddingResult.available(vector, "phase6-test-embedding");
            };
        }

        @Bean
        @Primary
        PlannerGateway deterministicPlanner() {
            return (projectId, userId, input) -> new ChapterPlan(
                    "青铜城调查事件",
                    "发现入口和龙文线索",
                    null,
                    List.of(new ScenePlan("水下入口", "发现线索", "路明非检查龙文机关", List.of(), List.of())),
                    List.of(),
                    List.of(),
                    "龙文机关需要血统或训练依据才能解析");
        }

        @Bean
        @Primary
        WorkflowWriterGateway deterministicWriter() {
            return (projectId, userId, input, chunks) -> {
                chunks.accept("路明非抵达三峡任务现场，记录青铜城水下入口，并按交接表接收完整七宗罪剑匣。");
                return new WriterResult("phase6-request", "phase6-writer", "stop", 100, 50, 0, 100, 10);
            };
        }

        @Bean
        @Primary
        ExtractorGateway deterministicExtractor() {
            return (projectId, userId, input) -> {
                boolean revised = input.context().contains("修订后");
                return new ExtractionResult(
                        revised ? "修订后知识结论回到待确认状态" : "路明非发现入口并接收完整七宗罪剑匣",
                        List.of("发现青铜城水下入口"),
                        List.of(revised ? "修订事实：龙王身份仍待证据确认" : "完整七宗罪剑匣由路明非接收"),
                        List.of(),
                        List.of(),
                        List.of());
            };
        }

        @Bean
        @Primary
        ReviewerGateway deterministicReviewer() {
            return (projectId, userId, input) -> new ReviewResult(List.of(), "语义审查通过");
        }

        @Bean
        @Primary
        AtomicCommitFaultInjector deterministicFaultInjector() {
            return run -> {
                if (run.getInstruction().contains("ROLLBACK_TEST")) {
                    throw new IllegalStateException("Injected atomic commit failure");
                }
            };
        }
    }
}

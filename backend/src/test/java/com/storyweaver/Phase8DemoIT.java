package com.storyweaver;

import static org.assertj.core.api.Assertions.assertThat;

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
class Phase8DemoIT {
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
        registry.add("storyweaver.security.jwt.secret", () -> "phase-eight-jwt-secret-at-least-32-bytes");
        registry.add("storyweaver.deepseek.api-key", () -> "phase-eight-contract-test-key");
        registry.add("storyweaver.workflow.event-poll-interval", () -> "20ms");
        registry.add("management.tracing.export.otlp.enabled", () -> "false");
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
    void completesTenConsecutiveChapterWorkflowsWithoutHalfCommittedState() throws Exception {
        String token = register();
        String projectId = createProject(token);
        String characterId = createCharacter(token, projectId);

        for (int chapterNo = 1; chapterNo <= 10; chapterNo++) {
            String chapterId = createChapter(token, projectId, chapterNo);
            JsonNode started = json(request(
                    "POST",
                    "/api/chapters/" + chapterId + "/workflows",
                    token,
                    Map.of(
                            "viewpointCharacterId",
                            characterId,
                            "instruction",
                            "连续回归第" + chapterNo + "个原创测试章：保持青铜城入口、龙文机关和人物知识线索连续"),
                    202,
                    "phase8-workflow-" + String.format("%02d", chapterNo)));
            JsonNode ready = awaitStatus(token, started.path("id").asString(), "WAITING_APPROVAL");
            if (chapterNo == 1) {
                assertThat(jdbc.queryForObject(
                                "select context_data->'previousChapter' from context_packet where workflow_run_id=?::uuid",
                                String.class,
                                ready.path("id").asString()))
                        .isEqualTo("{}");
            } else {
                assertThat(jdbc.queryForObject(
                                "select (context_data->'previousChapter'->>'chapterNo')::integer from context_packet where workflow_run_id=?::uuid",
                                Integer.class,
                                ready.path("id").asString()))
                        .isEqualTo(chapterNo - 1);
            }
            JsonNode completed = json(request(
                    "POST",
                    "/api/workflows/" + ready.path("id").asString() + "/approve",
                    token,
                    emptyApproval(ready.path("version").asLong()),
                    200,
                    null));
            assertThat(completed.path("status").asString()).isEqualTo("COMPLETED");
            assertThat(completed.path("committedVersionNo").asInt()).isEqualTo(1);
        }

        assertThat(jdbc.queryForObject(
                        "select count(*) from workflow_run where project_id=?::uuid and status='COMPLETED'",
                        Integer.class,
                        projectId))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject(
                        "select count(*) from chapter_version cv join chapter c on c.id=cv.chapter_id where c.project_id=?::uuid",
                        Integer.class,
                        projectId))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject(
                        "select count(*) from chapter c where c.project_id=?::uuid and c.current_version_no=0",
                        Integer.class,
                        projectId))
                .isZero();
    }

    private String register() throws Exception {
        return json(request(
                        "POST",
                        "/api/auth/register",
                        null,
                        Map.of(
                                "username",
                                "phase8-demo",
                                "email",
                                "phase8-demo@example.com",
                                "password",
                                "phase-eight-password"),
                        201,
                        null))
                .path("accessToken")
                .asString();
    }

    private String createProject(String token) throws Exception {
        return json(request(
                        "POST",
                        "/api/projects",
                        token,
                        Map.of(
                                "name", "龙族技术演示：青铜城十章回归",
                                "genre", "现代校园幻想 / 任务冒险",
                                "targetAudience", "GENERAL",
                                "narrativePerspective", "THIRD_PERSON",
                                "lengthType", "LONG_NOVEL",
                                "premise", "调查青铜城水下入口，揭开龙文机关背后的秘密。",
                                "description", "原创 Phase 8 连续工作流测试，不是原著真实章节",
                                "authorIntent", "人物知识、完整七宗罪剑匣归属和事件因果必须连续且可追溯",
                                "currentFocus", "调查青铜城水下入口和龙文机关",
                                "worldRules", List.of()),
                        201,
                        null))
                .path("id")
                .asString();
    }

    private String createCharacter(String token, String projectId) throws Exception {
        return json(request(
                        "POST",
                        "/api/projects/" + projectId + "/characters",
                        token,
                        Map.of(
                                "name",
                                "路明非",
                                "role",
                                "卡塞尔学院学生",
                                "description",
                                "青铜城调查任务的技术演示视角人物",
                                "state",
                                Map.of("lifeStatus", "ALIVE", "currentLocation", "三峡任务现场")),
                        201,
                        null))
                .path("id")
                .asString();
    }

    private String createChapter(String token, String projectId, int chapterNo) throws Exception {
        return json(request(
                        "POST",
                        "/api/projects/" + projectId + "/chapters",
                        token,
                        Map.of(
                                "chapterNo",
                                chapterNo,
                                "title",
                                "青铜城原创测试章 " + chapterNo,
                                "outline",
                                "路明非在三峡任务现场推进第 " + chapterNo + " 条青铜城调查证据"),
                        201,
                        null))
                .path("id")
                .asString();
    }

    private Map<String, Object> emptyApproval(long expectedVersion) {
        return Map.of(
                "expectedVersion",
                expectedVersion,
                "changeSummary",
                "Phase 8 continuous workflow regression",
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

    private JsonNode awaitStatus(String token, String runId, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        String current = null;
        while (System.nanoTime() < deadline) {
            JsonNode response = json(request("GET", "/api/workflows/" + runId, token, null, 200, null));
            current = response.path("status").asString();
            if (current.equals(expected)) return response;
            if (List.of("FAILED", "BLOCKED", "CANCELLED", "ROLLED_BACK").contains(current)) {
                throw new AssertionError("Workflow terminated as " + current + ": " + response);
            }
            Thread.sleep(30);
        }
        throw new AssertionError("Expected " + expected + " but was " + current);
    }

    private HttpResponse<String> request(
            String method, String path, String token, Object body, int expectedStatus, String idempotencyKey)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30));
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

    private JsonNode json(HttpResponse<String> response) {
        return objectMapper.readTree(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}

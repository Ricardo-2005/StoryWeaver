package com.storyweaver;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.usage.application.UsageService;
import com.storyweaver.usage.domain.UsageStatus;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase7ApiIT {
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
        registry.add("storyweaver.security.jwt.secret", () -> "phase-seven-jwt-secret-at-least-32-bytes");
        registry.add("spring.test.tracing.export", () -> "true");
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> "http://localhost:4318/v1/traces");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UsageService usage;

    @Autowired
    ApplicationContext context;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void exposesAuthenticatedStatelessMcpWithCandidateOnlyWritesAuditCostsMetricsAndTracing() throws Exception {
        Identity owner = identity("phase7-owner", "phase7-owner@example.com");
        Identity intruder = identity("phase7-intruder", "phase7-intruder@example.com");
        String projectId = createProject(owner.token(), "Phase 7 Project");
        String characterId = createCharacter(owner.token(), projectId, "路明非");
        String chapterId = createChapter(owner.token(), projectId);
        jdbc.update(
                """
                insert into item_ownership(
                    id, project_id, item_key, item_name, owner_character_id, item_status,
                    acquired_chapter_id, evidence, version, created_at, updated_at)
                values (?, ?::uuid, 'seven-sins-sword-case', '完整七宗罪剑匣', ?::uuid, 'ACTIVE', ?::uuid,
                    '路明非按青铜城任务交接记录接收完整七宗罪剑匣', 0, now(), now())
                """,
                UUID.randomUUID(),
                projectId,
                characterId,
                chapterId);

        request(
                "POST",
                "/mcp",
                null,
                Map.of("jsonrpc", "2.0", "id", 0, "method", "tools/list", "params", Map.of()),
                401);

        JsonNode initialized = mcp(
                owner.token(),
                1,
                "initialize",
                Map.of(
                        "protocolVersion", "2025-11-25",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "phase7-contract-test", "version", "1.0")));
        assertThat(initialized.path("result").path("serverInfo").path("name").asString())
                .isEqualTo("storyweaver-mcp");

        JsonNode tools = mcp(owner.token(), 2, "tools/list", Map.of());
        Set<String> toolNames = tools.path("result").path("tools").values().stream()
                .map(value -> value.path("name").asString())
                .collect(Collectors.toSet());
        assertThat(toolNames)
                .containsExactlyInAnyOrder(
                        "get_character_state",
                        "get_character_knowledge",
                        "get_worldbook_entries",
                        "get_recent_story_events",
                        "get_item_owner",
                        "save_candidate_fact");

        JsonNode resources = mcp(owner.token(), 3, "resources/templates/list", Map.of());
        assertThat(resources.path("result").path("resourceTemplates")).hasSize(5);
        JsonNode prompts = mcp(owner.token(), 4, "prompts/list", Map.of());
        assertThat(prompts.path("result").path("prompts").values().stream()
                        .map(value -> value.path("name").asString()))
                .containsExactlyInAnyOrder("plan-next-chapter", "review-chapter", "query-story-state");
        JsonNode resource = mcp(
                owner.token(), 41, "resources/read", Map.of("uri", "story://projects/" + projectId + "/author-intent"));
        assertThat(resource.path("result").path("contents").get(0).path("text").asString())
                .contains("保持人物、世界与时间线连续");
        JsonNode prompt = mcp(
                owner.token(),
                42,
                "prompts/get",
                Map.of("name", "query-story-state", "arguments", Map.of("projectId", projectId)));
        assertThat(prompt.path("result").path("messages")).isNotEmpty();
        for (String promptName : List.of("plan-next-chapter", "review-chapter")) {
            JsonNode chapterPrompt = mcp(
                    owner.token(),
                    43,
                    "prompts/get",
                    Map.of("name", promptName, "arguments", Map.of("projectId", projectId, "chapterId", chapterId)));
            assertThat(chapterPrompt.path("result").path("messages")).isNotEmpty();
        }
        for (String uri : List.of(
                "story://projects/" + projectId + "/current-outline",
                "story://projects/" + projectId + "/recent-summary",
                "story://characters/" + characterId + "/card",
                "story://characters/" + characterId + "/knowledge")) {
            JsonNode content = mcp(owner.token(), 44, "resources/read", Map.of("uri", uri));
            assertThat(content.path("result")
                            .path("contents")
                            .get(0)
                            .path("text")
                            .asString())
                    .isNotBlank();
        }

        JsonNode state = callTool(owner.token(), 5, "get_character_state", Map.of("characterId", characterId));
        assertThat(state.path("result").path("isError").asBoolean()).isFalse();
        assertThat(callTool(owner.token(), 51, "get_character_knowledge", Map.of("characterId", characterId))
                        .path("result")
                        .path("isError")
                        .asBoolean())
                .isFalse();
        assertThat(callTool(owner.token(), 52, "get_worldbook_entries", Map.of("projectId", projectId))
                        .path("result")
                        .path("isError")
                        .asBoolean())
                .isFalse();
        assertThat(callTool(owner.token(), 53, "get_recent_story_events", Map.of("projectId", projectId, "limit", 10))
                        .path("result")
                        .path("isError")
                        .asBoolean())
                .isFalse();
        assertThat(callTool(
                                owner.token(),
                                54,
                                "get_item_owner",
                                Map.of("projectId", projectId, "itemKey", "seven-sins-sword-case"))
                        .path("result")
                        .path("isError")
                        .asBoolean())
                .isFalse();
        assertThat(callTool(
                                owner.token(),
                                55,
                                "save_candidate_fact",
                                Map.of("projectId", projectId, "content", "缺少证据的候选事实"))
                        .path("result")
                        .path("isError")
                        .asBoolean())
                .isTrue();

        JsonNode saved = callTool(
                owner.token(),
                6,
                "save_candidate_fact",
                Map.of(
                        "projectId", projectId,
                        "factKey", "mcp-bronze-city-entrance",
                        "content", "青铜城水下入口由龙文机关控制",
                        "evidence", "路明非在三峡任务现场记录了龙文机关和入口位置",
                        "paragraphKey", "chapter-1-p3",
                        "requestKey", "phase7-candidate-001"));
        assertThat(saved.path("result").path("isError").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject(
                        "select count(*) from story_fact where project_id=?::uuid and source='MCP' and status='CANDIDATE'",
                        Integer.class,
                        projectId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from story_fact where project_id=?::uuid and status='ACCEPTED'",
                        Integer.class,
                        projectId))
                .isZero();

        callTool(
                owner.token(),
                7,
                "save_candidate_fact",
                Map.of(
                        "projectId", projectId,
                        "content", "重复请求不会重复写入",
                        "evidence", "相同 requestKey",
                        "requestKey", "phase7-candidate-001"));
        assertThat(jdbc.queryForObject(
                        "select count(*) from story_fact where project_id=?::uuid and source='MCP'",
                        Integer.class,
                        projectId))
                .isEqualTo(1);

        JsonNode denied = callTool(intruder.token(), 8, "get_worldbook_entries", Map.of("projectId", projectId));
        assertThat(denied.path("result").path("isError").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
                        "select count(*) from mcp_audit_log where project_id=?::uuid and outcome='FAILED'",
                        Integer.class,
                        projectId))
                .isEqualTo(1);
        assertThat(json(request("GET", "/api/projects/" + projectId + "/mcp-audit", owner.token(), null, 200))
                        .values())
                .anySatisfy(value ->
                        assertThat(value.path("operationName").asString()).isEqualTo("save_candidate_fact"));

        UUID ownerId =
                jdbc.queryForObject("select id from app_user where normalized_username='phase7-owner'", UUID.class);
        jdbc.update(
                """
                insert into pricing_rule(
                    id, rule_version, model, currency, input_per_million, output_per_million,
                    reasoning_per_million, cache_hit_per_million, cache_miss_per_million,
                    effective_from, active, created_at)
                values (?, 'phase7-test', 'phase7-model', 'CNY', 2, 8, 4, 0.5, 2, ?, true, ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                Timestamp.from(Instant.now()));
        usage.record(new UsageService.UsageInput(
                UUID.fromString(projectId),
                ownerId,
                "WRITER",
                "phase7-model",
                "phase7-request",
                UsageStatus.SUCCEEDED,
                1000,
                500,
                100,
                200,
                300,
                1,
                250));
        JsonNode costs = json(request("GET", "/api/projects/" + projectId + "/costs", owner.token(), null, 200));
        assertThat(costs.path("actualCost").decimalValue()).isPositive();
        assertThat(costs.path("unpricedRequests").asLong()).isZero();
        JsonNode initialBudget =
                json(request("GET", "/api/projects/" + projectId + "/budget", owner.token(), null, 200));
        assertThat(initialBudget.path("taskTokenLimit").asInt()).isEqualTo(40000);
        JsonNode updatedBudget = json(request(
                "PUT",
                "/api/projects/" + projectId + "/budget",
                owner.token(),
                Map.of(
                        "expectedVersion", initialBudget.path("version").asLong(),
                        "taskTokenLimit", 36000,
                        "userDailyCostLimit", 80,
                        "projectCostLimit", 800,
                        "writerOutputTokenLimit", 10000,
                        "plannerReasoningTokenLimit", 5000),
                200));
        assertThat(updatedBudget.path("taskTokenLimit").asInt()).isEqualTo(36000);
        assertThat(json(request("GET", "/api/pricing-rules", owner.token(), null, 200))
                        .values())
                .anySatisfy(value -> assertThat(value.path("model").asString()).isEqualTo("phase7-model"));
        assertThat(json(request("GET", "/api/projects/" + projectId + "/usage", owner.token(), null, 200)))
                .hasSize(1);

        String metrics = request("GET", "/actuator/prometheus", null, null, 200).body();
        assertThat(metrics)
                .contains("storyweaver_llm_requests_total")
                .contains("storyweaver_llm_latency_seconds")
                .contains("storyweaver_llm_input_tokens_total")
                .contains("storyweaver_llm_output_tokens_total")
                .contains("storyweaver_llm_cache_hit_tokens_total")
                .contains("storyweaver_llm_cost_total")
                .contains("storyweaver_sse_connections");
        assertThat(context.getBeansOfType(Tracer.class)).isNotEmpty();
    }

    private JsonNode callTool(String token, int id, String name, Map<String, Object> arguments) throws Exception {
        return mcp(token, id, "tools/call", Map.of("name", name, "arguments", arguments));
    }

    private JsonNode mcp(String token, int id, String method, Map<String, Object> params) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/mcp"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-11-25")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params))))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private Identity identity(String username, String email) throws Exception {
        JsonNode response = json(request(
                "POST",
                "/api/auth/register",
                null,
                Map.of("username", username, "email", email, "password", "phase-seven-password"),
                201));
        return new Identity(response.path("accessToken").asString());
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
                                "在连续的世界和时间线中推进故事。",
                                "worldRules",
                                List.of(),
                                "authorIntent",
                                "保持人物、世界与时间线连续"),
                        201))
                .path("id")
                .asString();
    }

    private String createCharacter(String token, String projectId, String name) throws Exception {
        return json(request(
                        "POST",
                        "/api/projects/" + projectId + "/characters",
                        token,
                        Map.of("name", name, "state", Map.of("lifeStatus", "ALIVE", "currentLocation", "三峡任务现场")),
                        201))
                .path("id")
                .asString();
    }

    private String createChapter(String token, String projectId) throws Exception {
        return json(request(
                        "POST",
                        "/api/projects/" + projectId + "/chapters",
                        token,
                        Map.of("chapterNo", 1, "title", "青铜城调查", "outline", "路明非在三峡发现青铜城入口并核对完整七宗罪剑匣归属"),
                        201))
                .path("id")
                .asString();
    }

    private HttpResponse<String> request(String method, String path, String token, Object body, int expectedStatus)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("Authorization", "Bearer " + token);
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
}

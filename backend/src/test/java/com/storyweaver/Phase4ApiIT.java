package com.storyweaver;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.llm.application.EmbeddingGateway;
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
        classes = {StoryWeaverApplication.class, Phase4ApiIT.DeterministicEmbeddingConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase4ApiIT {
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
        registry.add("storyweaver.security.jwt.secret", () -> "phase-four-jwt-secret-at-least-32-bytes");
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
    void activatesAllWorldbookModesTrimsTokensDegradesAndKeepsProjectsIsolated() throws Exception {
        String ownerToken = token("phase4-owner", "phase4-owner@example.com");
        String otherToken = token("phase4-other", "phase4-other@example.com");
        String projectId = createProject(ownerToken, "Phase 4 Project");
        String otherProjectId = createProject(otherToken, "Other Project");

        createWorldbookEntry(ownerToken, projectId, "混血种血统规则", "识别龙文和使用言灵必须有血统、训练或设备依据。", true, false, List.of(), 900);
        createWorldbookEntry(ownerToken, projectId, "龙文机关", "青铜城的龙文机关需要按调查证据逐步解析。", false, false, List.of("龙文"), 800);
        createWorldbookEntry(ownerToken, projectId, "青铜城水下结构", "青铜城入口位于三峡水下，进入前必须先完成勘测。", false, true, List.of(), 700);
        createWorldbookEntry(ownerToken, projectId, "低优先级长条目", "长".repeat(3000), true, false, List.of(), 1);
        createWorldbookEntry(otherToken, otherProjectId, "隔离秘党档案", "另一个项目的秘党测试档案。", true, true, List.of(), 1000);

        JsonNode preview = json(request(
                "POST",
                "/api/projects/" + projectId + "/worldbook/preview",
                ownerToken,
                Map.of("query", "路明非准备进入青铜城水下结构，识别龙文机关并遵守混血种血统规则", "tokenBudget", 200, "topK", 8),
                200));
        assertThat(preview.get("embeddingAvailable").asBoolean()).isTrue();
        assertThat(preview.toString()).contains("CONSTANT", "KEYWORD:龙文", "VECTOR:");
        assertThat(preview.toString()).contains("TOKEN_BUDGET").doesNotContain("隔离秘党档案");
        assertThat(preview.get("selectedTokens").asInt()).isLessThanOrEqualTo(200);

        JsonNode degraded = json(request(
                "POST",
                "/api/projects/" + projectId + "/worldbook/preview",
                ownerToken,
                Map.of("query", "龙文 FORCE_UNAVAILABLE", "tokenBudget", 200, "topK", 8),
                200));
        assertThat(degraded.get("embeddingAvailable").asBoolean()).isFalse();
        assertThat(degraded.get("degradedReason").asString()).isEqualTo("test_embedding_unavailable");
        assertThat(degraded.toString()).contains("CONSTANT", "KEYWORD:龙文").doesNotContain("VECTOR:");

        HttpResponse<String> forbidden = request(
                "POST",
                "/api/projects/" + projectId + "/worldbook/preview",
                otherToken,
                Map.of("query", "steal", "tokenBudget", 100),
                404);
        assertThat(forbidden.body()).contains("project_not_found");
        assertThat(jdbc.queryForObject(
                        "select count(*) from worldbook_entry where project_id=?::uuid and embedding is not null",
                        Integer.class,
                        projectId))
                .isGreaterThan(0);
        assertThat(jdbc.queryForObject(
                        "select count(*) from worldbook_entry where project_id=?::uuid", Integer.class, projectId))
                .isEqualTo(4);
    }

    @Test
    void storesAndRanksStoryEventsWithVectorAndStructuredSignals() throws Exception {
        String token = token("phase4-memory", "phase4-memory@example.com");
        String otherToken = token("phase4-memory-other", "phase4-memory-other@example.com");
        String projectId = createProject(token, "Memory Project");
        String otherProjectId = createProject(otherToken, "Other Memory Project");
        String characterId = json(request(
                        "POST",
                        "/api/projects/" + projectId + "/characters",
                        token,
                        Map.of("name", "路明非", "role", "卡塞尔学院学生"),
                        201))
                .get("id")
                .asString();
        String chapterId = json(request(
                        "POST",
                        "/api/projects/" + projectId + "/chapters",
                        token,
                        Map.of("chapterNo", 3, "title", "青铜城入口", "outline", "调查青铜城水下入口"),
                        201))
                .get("id")
                .asString();

        JsonNode importantEvent = json(request(
                "POST",
                "/api/projects/" + projectId + "/story-events",
                token,
                Map.of(
                        "chapterId", chapterId,
                        "participantIds", List.of(characterId),
                        "knownByIds", List.of(characterId),
                        "location", "青铜城水下入口",
                        "storyTime", "任务日第一时段",
                        "action", "路明非发现青铜城入口线索",
                        "result", "调查组获得进入水下结构的前置证据",
                        "importance", 0.95,
                        "evidenceParagraph", "p-3-17"),
                201));
        request(
                "POST",
                "/api/projects/" + projectId + "/story-events",
                token,
                Map.of(
                        "participantIds",
                        List.of(),
                        "knownByIds",
                        List.of(),
                        "location",
                        "卡塞尔学院",
                        "action",
                        "芬格尔整理任务档案",
                        "result",
                        "学院档案完成归档",
                        "importance",
                        0.1),
                201);
        request(
                "POST",
                "/api/projects/" + otherProjectId + "/story-events",
                otherToken,
                Map.of(
                        "participantIds",
                        List.of(),
                        "knownByIds",
                        List.of(),
                        "location",
                        "青铜城",
                        "action",
                        "另一个项目发现龙文机关",
                        "result",
                        "不可泄露",
                        "importance",
                        1.0),
                201);

        JsonNode search = json(request(
                "POST",
                "/api/projects/" + projectId + "/story-events/search",
                token,
                Map.of(
                        "query",
                        "寻找青铜城水下入口",
                        "participantIds",
                        List.of(characterId),
                        "location",
                        "青铜城水下入口",
                        "chapterNo",
                        4,
                        "topK",
                        5),
                200));
        assertThat(search.get("embeddingAvailable").asBoolean()).isTrue();
        assertThat(search.get("matches").get(0).get("event").get("id").asString())
                .isEqualTo(importantEvent.get("id").asString());
        assertThat(search.toString())
                .contains("SEMANTIC:", "PARTICIPANT_OVERLAP", "LOCATION", "CHAPTER_PROXIMITY:")
                .doesNotContain("不可泄露");
        assertThat(jdbc.queryForObject(
                        "select count(*) from story_event where project_id=?::uuid and embedding is not null",
                        Integer.class,
                        projectId))
                .isEqualTo(2);
    }

    private void createWorldbookEntry(
            String token,
            String projectId,
            String title,
            String content,
            boolean constant,
            boolean vector,
            List<String> keywords,
            int priority)
            throws Exception {
        request(
                "POST",
                "/api/projects/" + projectId + "/worldbook-entries",
                token,
                Map.of(
                        "title", title,
                        "content", content,
                        "active", true,
                        "constantEnabled", constant,
                        "vectorEnabled", vector,
                        "keywords", keywords,
                        "priority", priority,
                        "scopeType", "PROJECT",
                        "visibilityType", "ALL"),
                201);
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
                                "Fantasy",
                                "targetAudience",
                                "GENERAL",
                                "narrativePerspective",
                                "THIRD_PERSON",
                                "lengthType",
                                "LONG_NOVEL",
                                "premise",
                                "A fantasy project maintains a continuous story state.",
                                "worldRules",
                                List.of()),
                        201))
                .get("id")
                .asString();
    }

    private String token(String username, String email) throws Exception {
        return json(request(
                        "POST",
                        "/api/auth/register",
                        null,
                        Map.of("username", username, "email", email, "password", "Passw0rd!"),
                        201))
                .get("accessToken")
                .asString();
    }

    private HttpResponse<String> request(String method, String path, String token, Object body, int status)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return response;
    }

    private JsonNode json(HttpResponse<String> response) {
        return objectMapper.readTree(response.body());
    }

    @TestConfiguration
    static class DeterministicEmbeddingConfiguration {
        @Bean
        @Primary
        EmbeddingGateway deterministicEmbeddingGateway() {
            return text -> {
                if (text.contains("FORCE_UNAVAILABLE")) {
                    return EmbeddingGateway.EmbeddingResult.unavailable(
                            "test-embedding-512", "test_embedding_unavailable");
                }
                float[] vector = new float[512];
                text.codePoints().forEach(codePoint -> vector[Math.floorMod(codePoint, vector.length)] += 1.0f);
                double norm = 0.0;
                for (float value : vector) norm += value * value;
                float divisor = (float) Math.sqrt(Math.max(norm, 1.0));
                for (int i = 0; i < vector.length; i++) vector[i] /= divisor;
                return EmbeddingGateway.EmbeddingResult.available(vector, "test-embedding-512");
            };
        }
    }
}

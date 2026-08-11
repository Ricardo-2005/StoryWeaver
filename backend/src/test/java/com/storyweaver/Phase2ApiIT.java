package com.storyweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase2ApiIT {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-bookworm").asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:8.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("storyweaver.security.jwt.secret", () -> "phase-two-integration-test-secret-at-least-32-bytes");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void phaseTwoSupportsManualAssetsVersionsStableSkillCompositionAndOwnership() throws Exception {
        String ownerToken = token("phase2-owner", "phase2-owner@example.com");
        String otherToken = token("phase2-other", "phase2-other@example.com");
        JsonNode project = json(post(
                "/api/projects",
                ownerToken,
                Map.of(
                        "name",
                        "Manual Writing Project",
                        "genre",
                        "Fantasy",
                        "targetAudience",
                        "GENERAL",
                        "narrativePerspective",
                        "THIRD_PERSON",
                        "lengthType",
                        "LONG_NOVEL",
                        "premise",
                        "A manual writing project begins with a clear premise.",
                        "worldRules",
                        List.of()),
                201));
        String projectId = project.get("id").asString();

        Map<String, Object> characterBody = new LinkedHashMap<>();
        characterBody.put("name", "Miao Lanzhou");
        characterBody.put("role", "PROTAGONIST");
        characterBody.put("personality", "Cautious but loyal");
        characterBody.put("goals", "Reach the sacred mountain");
        characterBody.put(
                "state",
                Map.of(
                        "lifeStatus", "ALIVE",
                        "currentLocation", "Harbor",
                        "physicalCondition", "Healthy",
                        "abilities", "Swordsmanship"));
        JsonNode character = json(post("/api/projects/" + projectId + "/characters", ownerToken, characterBody, 201));
        String characterId = character.get("id").asString();
        assertThat(character.get("state").get("lifeStatus").asString()).isEqualTo("ALIVE");

        JsonNode deadState = json(put(
                "/api/characters/" + characterId + "/state",
                ownerToken,
                Map.of("lifeStatus", "DEAD", "currentLocation", "Sacred Mountain", "expectedVersion", 0),
                200));
        assertThat(deadState.get("version").asLong()).isEqualTo(1);
        assertThat(deadState.get("lifeStatus").asString()).isEqualTo("DEAD");
        assertThat(put(
                                "/api/characters/" + characterId + "/state",
                                ownerToken,
                                Map.of("lifeStatus", "ALIVE", "expectedVersion", 0),
                                409)
                        .body())
                .contains("optimistic_lock_conflict");
        assertThat(get("/api/characters/" + characterId, otherToken).statusCode())
                .isEqualTo(404);

        JsonNode master = json(post(
                "/api/projects/" + projectId + "/outlines",
                ownerToken,
                Map.of("nodeType", "MASTER", "title", "Master plot", "sequenceNo", 0),
                201));
        JsonNode volume = json(post(
                "/api/projects/" + projectId + "/outlines",
                ownerToken,
                Map.of(
                        "parentId",
                        master.get("id").asString(),
                        "nodeType",
                        "VOLUME",
                        "title",
                        "Volume I",
                        "sequenceNo",
                        0),
                201));
        JsonNode chapterOutline = json(post(
                "/api/projects/" + projectId + "/outlines",
                ownerToken,
                Map.of(
                        "parentId",
                        volume.get("id").asString(),
                        "nodeType",
                        "CHAPTER",
                        "title",
                        "Arrival",
                        "sequenceNo",
                        0),
                201));
        assertThat(json(get("/api/projects/" + projectId + "/outlines", ownerToken))
                        .size())
                .isEqualTo(3);

        JsonNode chapter = json(post(
                "/api/projects/" + projectId + "/chapters",
                ownerToken,
                Map.of(
                        "chapterNo",
                        1,
                        "title",
                        "Arrival",
                        "outlineNodeId",
                        chapterOutline.get("id").asString(),
                        "outline",
                        "The hero reaches the mountain."),
                201));
        String chapterId = chapter.get("id").asString();
        assertThat(chapter.get("currentVersionNo").asInt()).isZero();

        JsonNode versionOne = json(post(
                "/api/chapters/" + chapterId + "/versions",
                ownerToken,
                Map.of(
                        "title",
                        "Arrival",
                        "content",
                        "First manuscript",
                        "summary",
                        "The arrival",
                        "changeSummary",
                        "Initial manual draft",
                        "expectedVersion",
                        0),
                201));
        assertThat(versionOne.get("currentVersionNo").asInt()).isEqualTo(1);
        JsonNode versionTwo = json(post(
                "/api/chapters/" + chapterId + "/versions",
                ownerToken,
                Map.of("title", "Arrival revised", "content", "Second manuscript", "expectedVersion", 1),
                201));
        assertThat(versionTwo.get("currentVersionNo").asInt()).isEqualTo(2);
        JsonNode restored = json(post(
                "/api/chapters/" + chapterId + "/restore/1",
                ownerToken,
                Map.of("changeSummary", "Restore selected baseline", "expectedVersion", 2),
                200));
        assertThat(restored.get("currentVersionNo").asInt()).isEqualTo(3);
        assertThat(restored.get("currentVersion").get("restoredFromVersionNo").asInt())
                .isEqualTo(1);
        assertThat(restored.get("currentVersion").get("content").asString()).isEqualTo("First manuscript");
        assertThat(json(get("/api/chapters/" + chapterId + "/versions", ownerToken))
                        .size())
                .isEqualTo(3);

        createSkill(
                projectId, ownerToken, "Base", "BASE", null, Map.of("POV_MODE", "THIRD_PERSON", "PACING", "MEDIUM"));
        createSkill(projectId, ownerToken, "Project", "PROJECT", null, Map.of("PACING", "FAST"));
        createSkill(projectId, ownerToken, "Chapter", "CHAPTER", chapterId, Map.of("PACING", "SLOW"));

        JsonNode composed = json(post(
                "/api/projects/" + projectId + "/skills/compose", ownerToken, Map.of("chapterId", chapterId), 200));
        assertThat(composed.get("resolved").asBoolean()).isTrue();
        assertThat(composed.get("effectiveRules").get("PACING").get("value").asString())
                .isEqualTo("SLOW");
        assertThat(composed.get("effectiveRules").get("POV_MODE").get("scope").asString())
                .isEqualTo("BASE");

        createSkill(projectId, ownerToken, "Conflicting chapter", "CHAPTER", chapterId, Map.of("PACING", "VERY_FAST"));
        JsonNode conflict = json(post(
                "/api/projects/" + projectId + "/skills/compose", ownerToken, Map.of("chapterId", chapterId), 200));
        assertThat(conflict.get("resolved").asBoolean()).isFalse();
        assertThat(conflict.get("conflicts").get(0).get("scope").asString()).isEqualTo("CHAPTER");
        assertThat(conflict.get("conflicts").get(0).get("key").asString()).isEqualTo("PACING");
        assertThat(post(
                                "/api/projects/" + projectId + "/skills/compose",
                                otherToken,
                                Map.of("chapterId", chapterId),
                                404)
                        .body())
                .contains("project_not_found");

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from chapter_version where chapter_id = ?::uuid", Integer.class, chapterId))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("select to_regclass('mcp_audit_log')", String.class))
                .isEqualTo("mcp_audit_log");
        assertThat(jdbcTemplate.queryForObject("select to_regclass('agent_execution')", String.class))
                .isNull();
    }

    private void createSkill(
            String projectId, String token, String name, String scope, String chapterId, Map<String, String> rules)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("rules", rules);
        body.put("enabled", true);
        body.put("scope", scope);
        if (chapterId != null) body.put("chapterId", chapterId);
        post("/api/projects/" + projectId + "/skills", token, body, 201);
    }

    private String token(String username, String email) throws Exception {
        return json(post(
                        "/api/auth/register",
                        null,
                        Map.of("username", username, "email", email, "password", "Passw0rd!"),
                        201))
                .get("accessToken")
                .asString();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = request(path).GET();
        authorize(builder, token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, Object body, int expectedStatus) throws Exception {
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder, token);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response;
    }

    private HttpResponse<String> put(String path, String token, Object body, int expectedStatus) throws Exception {
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder, token);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20));
    }

    private void authorize(HttpRequest.Builder builder, String token) {
        if (token != null) builder.header("Authorization", "Bearer " + token);
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }
}

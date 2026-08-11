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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase1ApiIT {

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
        registry.add("storyweaver.security.jwt.secret", () -> "phase-one-integration-test-secret-at-least-32-bytes");
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
    void phaseOneApiEnforcesOwnershipVersionsAndJwtAuthentication() throws Exception {
        HttpResponse<String> unauthenticated = get("/api/me", null);
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(unauthenticated.headers().firstValue("content-type").orElse(""))
                .startsWith("application/problem+json");

        JsonNode aliceRegistration = json(post(
                "/api/auth/register",
                null,
                Map.of("username", "alice", "email", "alice@example.com", "password", "Passw0rd!"),
                201));
        String aliceToken = aliceRegistration.get("accessToken").asString();
        assertThat(aliceRegistration.get("user").get("role").asString()).isEqualTo("USER");
        JsonNode bobRegistration = json(post(
                "/api/auth/register",
                null,
                Map.of("username", "bob", "email", "bob@example.com", "password", "Passw0rd!"),
                201));
        String bobToken = bobRegistration.get("accessToken").asString();

        HttpResponse<String> duplicateRegistration = post(
                "/api/auth/register",
                null,
                Map.of("username", "alice", "email", "other@example.com", "password", "Passw0rd!"),
                409);
        assertThat(json(duplicateRegistration).get("code").asString()).isEqualTo("username_exists");
        HttpResponse<String> invalidLogin =
                post("/api/auth/login", null, Map.of("identifier", "alice", "password", "wrong-password"), 401);
        assertThat(json(invalidLogin).get("code").asString()).isEqualTo("invalid_credentials");

        JsonNode login = json(
                post("/api/auth/login", null, Map.of("identifier", "ALICE@EXAMPLE.COM", "password", "Passw0rd!"), 200));
        aliceToken = login.get("accessToken").asString();
        JsonNode me = json(get("/api/me", aliceToken));
        assertThat(me.get("username").asString()).isEqualTo("alice");
        assertThat(me.get("role").asString()).isEqualTo("USER");

        String passwordHash = jdbcTemplate.queryForObject(
                "select password_hash from app_user where normalized_username = 'alice'", String.class);
        assertThat(passwordHash).startsWith("{bcrypt}").doesNotContain("Passw0rd!");

        JsonNode project = json(post(
                "/api/projects",
                aliceToken,
                Map.of(
                        "name", "The Long Night",
                        "genre", "Fantasy",
                        "targetAudience", "GENERAL",
                        "narrativePerspective", "THIRD_PERSON",
                        "lengthType", "LONG_NOVEL",
                        "premise", "A long night exposes a hidden threat.",
                        "description", "A continuity test project",
                        "authorIntent", "Keep causality explicit",
                        "currentFocus", "Opening arc",
                        "worldRules", List.of()),
                201));
        String projectId = project.get("id").asString();
        assertThat(project.get("version").asLong()).isZero();
        assertThat(json(get("/api/projects", aliceToken)).size()).isEqualTo(1);

        HttpResponse<String> crossProjectRead = get("/api/projects/" + projectId, bobToken);
        assertThat(crossProjectRead.statusCode()).isEqualTo(404);
        HttpResponse<String> crossProjectWrite = post(
                "/api/projects/" + projectId + "/assets",
                bobToken,
                Map.of("assetType", "RULE", "name", "Forbidden", "content", "Must not persist"),
                404);
        assertThat(json(crossProjectWrite).get("code").asString()).isEqualTo("project_not_found");

        JsonNode asset = json(post(
                "/api/projects/" + projectId + "/assets",
                aliceToken,
                Map.of(
                        "assetType", "RULE",
                        "name", "Moon rule",
                        "content", "The moon is always red.",
                        "changeSummary", "Initial canon"),
                201));
        String assetId = asset.get("id").asString();
        assertThat(asset.get("status").asString()).isEqualTo("DRAFT");
        assertThat(asset.get("currentVersionNo").asInt()).isEqualTo(1);
        assertThat(asset.get("version").asLong()).isZero();

        JsonNode revised = json(put(
                "/api/assets/" + assetId,
                aliceToken,
                Map.of(
                        "name", "Moon rule",
                        "content", "The moon turns red after midnight.",
                        "changeSummary", "Clarified timing",
                        "expectedVersion", 0),
                200));
        assertThat(revised.get("currentVersionNo").asInt()).isEqualTo(2);
        assertThat(revised.get("version").asLong()).isEqualTo(1);
        assertThat(revised.get("currentVersion").get("content").asString())
                .isEqualTo("The moon turns red after midnight.");

        HttpResponse<String> staleAssetUpdate = put(
                "/api/assets/" + assetId,
                aliceToken,
                Map.of(
                        "name", "Stale edit",
                        "content", "Must not persist",
                        "expectedVersion", 0),
                409);
        assertThat(json(staleAssetUpdate).get("code").asString()).isEqualTo("optimistic_lock_conflict");

        HttpResponse<String> crossAssetConfirm =
                post("/api/assets/" + assetId + "/confirm", bobToken, Map.of("expectedVersion", 1), 404);
        assertThat(json(crossAssetConfirm).get("code").asString()).isEqualTo("project_not_found");

        JsonNode confirmed =
                json(post("/api/assets/" + assetId + "/confirm", aliceToken, Map.of("expectedVersion", 1), 200));
        assertThat(confirmed.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(confirmed.get("confirmedVersionNo").asInt()).isEqualTo(2);
        assertThat(confirmed.get("version").asLong()).isEqualTo(2);
        JsonNode assetList = json(get("/api/projects/" + projectId + "/assets", aliceToken));
        assertThat(assetList.size()).isEqualTo(1);
        assertThat(assetList.get(0).get("status").asString()).isEqualTo("CONFIRMED");

        JsonNode updatedProject = json(put(
                "/api/projects/" + projectId,
                aliceToken,
                Map.of(
                        "name", "The Long Night Revised",
                        "genre", "Fantasy",
                        "targetAudience", "GENERAL",
                        "narrativePerspective", "THIRD_PERSON",
                        "lengthType", "LONG_NOVEL",
                        "premise", "A long night exposes a hidden threat.",
                        "worldRules", List.of(),
                        "archived", false,
                        "expectedVersion", 0),
                200));
        assertThat(updatedProject.get("version").asLong()).isEqualTo(1);

        HttpResponse<String> staleProjectUpdate = put(
                "/api/projects/" + projectId,
                aliceToken,
                Map.of(
                        "name",
                        "Stale project",
                        "genre",
                        "Fantasy",
                        "targetAudience",
                        "GENERAL",
                        "narrativePerspective",
                        "THIRD_PERSON",
                        "lengthType",
                        "LONG_NOVEL",
                        "premise",
                        "A long night exposes a hidden threat.",
                        "worldRules",
                        List.of(),
                        "archived",
                        false,
                        "expectedVersion",
                        0),
                409);
        assertThat(json(staleProjectUpdate).get("code").asString()).isEqualTo("optimistic_lock_conflict");

        JsonNode snapshot =
                json(post("/api/projects/" + projectId + "/snapshots", aliceToken, Map.of("expectedVersion", 1), 201));
        assertThat(snapshot.get("projectVersion").asLong()).isEqualTo(1);

        Integer versionCount = jdbcTemplate.queryForObject(
                "select count(*) from canon_asset_version where asset_id = ?::uuid", Integer.class, assetId);
        Integer snapshottedAssets = jdbcTemplate.queryForObject(
                "select jsonb_array_length(snapshot_json -> 'canonAssets') from project_snapshot where id = ?::uuid",
                Integer.class,
                snapshot.get("id").asString());
        assertThat(versionCount).isEqualTo(2);
        assertThat(snapshottedAssets).isEqualTo(1);

        JsonNode deprecated =
                json(post("/api/assets/" + assetId + "/deprecate", aliceToken, Map.of("expectedVersion", 2), 200));
        assertThat(deprecated.get("status").asString()).isEqualTo("DEPRECATED");
        HttpResponse<String> editDeprecated = put(
                "/api/assets/" + assetId,
                aliceToken,
                Map.of("name", "Forbidden edit", "content", "Must not persist", "expectedVersion", 3),
                409);
        assertThat(json(editDeprecated).get("code").asString()).isEqualTo("asset_deprecated");

        HttpResponse<String> crossProjectDelete =
                delete("/api/projects/" + projectId + "?expectedVersion=1", bobToken, 404);
        assertThat(json(crossProjectDelete).get("code").asString()).isEqualTo("project_not_found");

        HttpResponse<String> activeProjectDelete =
                delete("/api/projects/" + projectId + "?expectedVersion=1", aliceToken, 409);
        assertThat(json(activeProjectDelete).get("code").asString()).isEqualTo("project_not_archived");

        JsonNode archivedProject = json(put("/api/projects/" + projectId, aliceToken, projectUpdateBody(true, 1), 200));
        assertThat(archivedProject.get("archived").asBoolean()).isTrue();
        assertThat(archivedProject.get("version").asLong()).isEqualTo(2);
        assertThat(json(get("/api/projects", aliceToken)).size()).isZero();
        JsonNode projectsIncludingArchived = json(get("/api/projects?includeArchived=true", aliceToken));
        assertThat(projectsIncludingArchived.size()).isEqualTo(1);
        assertThat(projectsIncludingArchived.get(0).get("archived").asBoolean()).isTrue();

        JsonNode restoredProject =
                json(put("/api/projects/" + projectId, aliceToken, projectUpdateBody(false, 2), 200));
        assertThat(restoredProject.get("archived").asBoolean()).isFalse();
        assertThat(restoredProject.get("version").asLong()).isEqualTo(3);
        assertThat(json(get("/api/projects", aliceToken)).size()).isEqualTo(1);

        JsonNode archivedAgain = json(put("/api/projects/" + projectId, aliceToken, projectUpdateBody(true, 3), 200));
        assertThat(archivedAgain.get("version").asLong()).isEqualTo(4);
        HttpResponse<String> staleDelete = delete("/api/projects/" + projectId + "?expectedVersion=3", aliceToken, 409);
        assertThat(json(staleDelete).get("code").asString()).isEqualTo("optimistic_lock_conflict");

        delete("/api/projects/" + projectId + "?expectedVersion=4", aliceToken, 204);
        assertThat(get("/api/projects/" + projectId, aliceToken).statusCode()).isEqualTo(404);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from novel_project where id = ?::uuid", Integer.class, projectId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from canon_asset where id = ?::uuid", Integer.class, assetId))
                .isZero();
    }

    private Map<String, Object> projectUpdateBody(boolean archived, long expectedVersion) {
        return Map.of(
                "name", "The Long Night Revised",
                "genre", "Fantasy",
                "targetAudience", "GENERAL",
                "narrativePerspective", "THIRD_PERSON",
                "lengthType", "LONG_NOVEL",
                "premise", "A long night exposes a hidden threat.",
                "worldRules", List.of(),
                "archived", archived,
                "expectedVersion", expectedVersion);
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

    private HttpResponse<String> delete(String path, String token, int expectedStatus) throws Exception {
        HttpRequest.Builder builder = request(path).DELETE();
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
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }
}

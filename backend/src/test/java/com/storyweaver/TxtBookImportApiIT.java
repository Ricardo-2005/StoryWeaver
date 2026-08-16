package com.storyweaver;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.importing.book.application.BookReconstructionService;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
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
class TxtBookImportApiIT {
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-bookworm").asCompatibleSubstituteFor("postgres");
    private static final Path STORAGE = temporaryStorage();

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
        registry.add("storyweaver.security.jwt.secret", () -> "txt-import-integration-secret-at-least-32-bytes");
        registry.add("storyweaver.import.txt.storage-directory", STORAGE::toString);
        registry.add("storyweaver.embedding.enabled", () -> "false");
        registry.add("management.tracing.export.otlp.enabled", () -> "false");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BookReconstructionService reconstruction;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void uploadsPreviewsEditsAndCommitsTxtWithoutAiWhileEnforcingOwnership() throws Exception {
        String owner = register("txt-owner", "txt-owner@example.com");
        String other = register("txt-other", "txt-other@example.com");
        byte[] source =
                """
                序章
                这是序章正文。

                第001章 雾港
                他在正文里提到“第一章”三个字，不能再次切分。
                """
                        .getBytes(StandardCharsets.UTF_8);

        JsonNode uploaded = json(upload(owner, "../evil.txt", source, 201));
        String importId = uploaded.path("id").asString();
        assertThat(uploaded.path("filename").asString()).isEqualTo("evil.txt");
        assertThat(uploaded.path("status").asString()).isEqualTo("UPLOADED");
        assertThat(uploaded.path("detectedEncoding").asString()).isEqualTo("UTF-8");
        assertThat(uploaded.path("sha256").asString()).hasSize(64);

        JsonNode duplicate = json(upload(owner, "same-content.txt", source, 201));
        assertThat(duplicate.path("duplicateImportId").asString()).isEqualTo(importId);
        request("POST", "/api/txt-imports/" + duplicate.path("id").asString() + "/cancel", owner, null, 200);

        request("GET", "/api/txt-imports/" + importId, other, null, 404);

        JsonNode parsed = json(
                request("POST", "/api/txt-imports/" + importId + "/parse", owner, Map.of("encoding", "AUTO"), 200));
        assertThat(parsed.path("status").asString()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(parsed.path("parserVersion").asString()).isEqualTo("txt-lines-v2");
        assertThat(parsed.path("headingCount").asInt()).isEqualTo(2);
        assertThat(parsed.path("chapters")).hasSize(2);

        String firstId = parsed.path("chapters").get(0).path("id").asString();
        JsonNode edited = json(request(
                "PATCH",
                "/api/txt-imports/" + importId + "/chapters/" + firstId,
                owner,
                Map.of("expectedVersion", parsed.path("version").asLong(), "title", "自定义序章", "included", true),
                200));
        assertThat(edited.path("chapters").get(0).path("title").asString()).isEqualTo("自定义序章");

        JsonNode committed = json(request(
                "POST",
                "/api/txt-imports/" + importId + "/commit",
                owner,
                Map.of(
                        "expectedVersion",
                        edited.path("version").asLong(),
                        "project",
                        Map.ofEntries(
                                Map.entry("name", "雾港导入测试"),
                                Map.entry("genre", "FANTASY_GENERAL"),
                                Map.entry("targetAudience", "GENERAL"),
                                Map.entry("narrativePerspective", "THIRD_PERSON"),
                                Map.entry("lengthType", "LONG_NOVEL"),
                                Map.entry("premise", "从 TXT 导入的完整回归测试故事构想。"),
                                Map.entry("description", "导入回归"),
                                Map.entry("worldRules", List.of()))),
                201));
        String projectId = committed.path("projectId").asString();
        assertThat(committed.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(committed.path("processedChapters").asInt()).isEqualTo(2);
        assertThat(committed.path("analysisStatus").asString()).isEqualTo("NOT_REQUESTED");

        JsonNode chapters = json(request("GET", "/api/projects/" + projectId + "/chapters", owner, null, 200));
        assertThat(chapters).hasSize(2);
        assertThat(chapters.get(0).path("title").asString()).isEqualTo("自定义序章");
        assertThat(chapters.get(1).path("currentVersion").path("content").asString())
                .contains("第一章")
                .doesNotContain("第001章 雾港");

        assertThat(jdbc.queryForObject(
                        "SELECT creation_source FROM novel_project WHERE id=?::uuid", String.class, projectId))
                .isEqualTo("TXT_IMPORT");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM chapter_version WHERE project_id=?::uuid AND creation_source='TXT_IMPORT' AND source_hash IS NOT NULL AND source_encoding='UTF-8' AND parser_version='txt-lines-v2'",
                        Integer.class,
                        projectId))
                .isEqualTo(2);

        JsonNode project = json(request("GET", "/api/projects/" + projectId, owner, null, 200));
        assertThat(project.path("creationSource").asString()).isEqualTo("TXT_IMPORT");
        assertThat(project.path("reconstructionStatus").asString()).isEqualTo("NOT_ANALYZED");

        JsonNode estimate = json(request(
                "POST",
                "/api/projects/" + projectId + "/reconstruction/estimate",
                owner,
                Map.of(
                        "mode", "STANDARD",
                        "includeSkillDistillation", true,
                        "includeForeshadowing", true),
                200));
        assertThat(estimate.path("chapters").asInt()).isEqualTo(2);
        assertThat(estimate.path("chunks").asInt()).isEqualTo(2);
        assertThat(estimate.path("estimatedCalls").asInt()).isGreaterThan(2);
        assertThat(estimate.path("estimatedInputTokens").asLong()).isPositive();

        JsonNode status = json(request("GET", "/api/projects/" + projectId + "/reconstruction", owner, null, 200));
        assertThat(status.path("status").asString()).isEqualTo("NOT_ANALYZED");
        request("GET", "/api/projects/" + projectId + "/reconstruction", other, null, 404);

        JsonNode started = json(request(
                "POST",
                "/api/projects/" + projectId + "/reconstruction",
                owner,
                Map.of(
                        "mode",
                        "STANDARD",
                        "includeSkillDistillation",
                        true,
                        "includeForeshadowing",
                        true,
                        "maxBudget",
                        0),
                202));
        assertThat(started.path("status").asString())
                .isIn("QUEUED", "PREPROCESSING", "CHAPTER_ANALYSIS", "PAUSED_BUDGET");
        JsonNode paused = awaitStatus(projectId, owner, "PAUSED_BUDGET");
        assertThat(paused.path("processedChunks").asInt()).isZero();
        assertThat(paused.path("totalChunks").asInt()).isEqualTo(2);
        ReflectionTestUtils.invokeMethod(
                reconstruction,
                "finishValidation",
                UUID.fromString(started.path("id").asString()),
                "WAITING_REVIEW");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM book_reconstruction_job WHERE id=?::uuid",
                        String.class,
                        started.path("id").asString()))
                .isEqualTo("WAITING_REVIEW");
        assertThat(jdbc.queryForObject(
                        "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class))
                .isEqualTo("19");
    }

    private String register(String username, String email) throws Exception {
        return json(request(
                        "POST",
                        "/api/auth/register",
                        null,
                        Map.of("username", username, "email", email, "password", "txt-import-password"),
                        201))
                .path("accessToken")
                .asString();
    }

    private HttpResponse<String> upload(String token, String filename, byte[] content, int expected) throws Exception {
        String boundary = "StoryWeaver" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/imports/txt"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
        return response;
    }

    private HttpResponse<String> request(String method, String path, String token, Object body, int expected)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/problem+json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        request.method(method, publisher);
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
        return response;
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private JsonNode awaitStatus(String projectId, String token, String expected) throws Exception {
        JsonNode current = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            current = json(request("GET", "/api/projects/" + projectId + "/reconstruction", token, null, 200));
            if (expected.equals(current.path("status").asString())) return current;
            Thread.sleep(100);
        }
        throw new AssertionError("Expected reconstruction status " + expected + " but was " + current);
    }

    private static Path temporaryStorage() {
        try {
            return Files.createTempDirectory("storyweaver-txt-import-it-");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

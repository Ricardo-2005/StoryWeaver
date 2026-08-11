package com.storyweaver;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
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
class Phase3ApiIT {
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-bookworm").asCompatibleSubstituteFor("postgres");
    private static final WireMockServer DEEPSEEK = new WireMockServer(options().dynamicPort());

    static {
        DEEPSEEK.start();
    }

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
        registry.add("storyweaver.security.jwt.secret", () -> "phase-three-jwt-secret-at-least-32-bytes");
        registry.add("storyweaver.deepseek.base-url", DEEPSEEK::baseUrl);
        registry.add("storyweaver.deepseek.api-key", () -> "contract-test-api-key");
        registry.add("storyweaver.deepseek.user-id-secret", () -> "contract-test-hmac-secret");
        registry.add("storyweaver.deepseek.retry-initial-backoff", () -> "0ms");
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @AfterAll
    static void stopDeepSeek() {
        DEEPSEEK.stop();
    }

    @Test
    void phaseThreeRecoversJsonStreamsWriterPersistsUsageAndEnforcesOwnership() throws Exception {
        DEEPSEEK.resetAll();
        configurePlannerRecovery();
        configureWriterStream();

        String ownerToken = token("phase3-owner", "phase3-owner@example.com");
        String otherToken = token("phase3-other", "phase3-other@example.com");
        JsonNode project = json(request(
                "POST",
                "/api/projects",
                ownerToken,
                Map.of(
                        "name",
                        "AI Contract Project",
                        "genre",
                        "Fantasy",
                        "targetAudience",
                        "GENERAL",
                        "narrativePerspective",
                        "THIRD_PERSON",
                        "lengthType",
                        "LONG_NOVEL",
                        "premise",
                        "A hero arrives at a gate that changes everything.",
                        "worldRules",
                        List.of()),
                201));
        String projectId = project.get("id").asString();

        JsonNode plan = json(request(
                "POST",
                "/api/projects/" + projectId + "/ai/planner",
                ownerToken,
                Map.of("instruction", "Plan chapter one", "context", "The hero arrives at the gate."),
                200));
        assertThat(plan.get("chapterTitle").asString()).isEqualTo("The Gate");
        assertThat(plan.get("scenes").size()).isEqualTo(1);

        HttpResponse<String> forbidden = request(
                "POST",
                "/api/projects/" + projectId + "/ai/planner",
                otherToken,
                Map.of("instruction", "Steal context", "context", "No access"),
                404);
        assertThat(forbidden.body()).contains("project_not_found");

        HttpResponse<String> writer = request(
                "POST",
                "/api/projects/" + projectId + "/ai/writer",
                ownerToken,
                Map.of("instruction", "Write the opening", "context", "The gate opens."),
                200);
        assertThat(writer.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");
        assertThat(writer.body())
                .contains("event:text.delta")
                .contains("\"text\":\"Hel\"")
                .contains("\"text\":\"lo\"")
                .contains("text.completed")
                .doesNotContain("vendor-keep-alive");

        JsonNode usage = json(get("/api/projects/" + projectId + "/usage", ownerToken));
        assertThat(usage.size()).isEqualTo(2);
        assertThat(usage.toString()).contains("PLANNER", "WRITER");
        Integer plannerAttempts = jdbcTemplate.queryForObject(
                "select attempts from usage_record where project_id=?::uuid and agent='PLANNER'",
                Integer.class,
                projectId);
        Integer cacheHit = jdbcTemplate.queryForObject(
                "select prompt_cache_hit_tokens from usage_record where project_id=?::uuid and agent='PLANNER'",
                Integer.class,
                projectId);
        assertThat(plannerAttempts).isEqualTo(2);
        assertThat(cacheHit).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("select to_regclass('mcp_audit_log')", String.class))
                .isEqualTo("mcp_audit_log");

        List<com.github.tomakehurst.wiremock.stubbing.ServeEvent> calls =
                DEEPSEEK.getAllServeEvents().stream().toList();
        JsonNode plannerRequest = calls.stream()
                .map(event -> event.getRequest().getBodyAsString())
                .map(this::read)
                .filter(body -> !body.get("stream").asBoolean())
                .findFirst()
                .orElseThrow();
        assertThat(plannerRequest.has("temperature")).isFalse();
        assertThat(plannerRequest.has("presence_penalty")).isFalse();
        assertThat(plannerRequest.has("frequency_penalty")).isFalse();
        assertThat(plannerRequest.get("thinking").get("type").asString()).isEqualTo("enabled");
        assertThat(plannerRequest.get("user_id").asString()).startsWith("sw_").doesNotContain("phase3-owner");

        DEEPSEEK.verify(
                3,
                postRequestedFor(urlEqualTo("/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer contract-test-api-key")));
    }

    @Test
    void retriesTransientResponsesAndStopsOnNonRetryableAuthenticationFailure() throws Exception {
        DEEPSEEK.resetAll();
        DEEPSEEK.stubFor(post(urlEqualTo("/chat/completions"))
                .inScenario("transient-recovery")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("server-error")
                .willReturn(aResponse().withStatus(429)));
        DEEPSEEK.stubFor(post(urlEqualTo("/chat/completions"))
                .inScenario("transient-recovery")
                .whenScenarioStateIs("server-error")
                .willSetStateTo("recovered")
                .willReturn(aResponse().withStatus(503)));
        Map<String, Object> extraction = Map.of(
                "summary", "The hero opens the gate.",
                "events", List.of("The gate opens"),
                "candidateFacts", List.of("The gate is open"),
                "characterChanges", List.of("The hero entered the city"),
                "itemTransfers", List.of(),
                "knowledgeTransfers", List.of());
        DEEPSEEK.stubFor(post(urlEqualTo("/chat/completions"))
                .inScenario("transient-recovery")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(completion(objectMapper.writeValueAsString(extraction), "", "stop", 8, 4, 0, 0, 8))));

        String token = token("phase3-retry", "phase3-retry@example.com");
        String projectId = json(request(
                        "POST",
                        "/api/projects",
                        token,
                        Map.of(
                                "name",
                                "Retry Contract Project",
                                "genre",
                                "Fantasy",
                                "targetAudience",
                                "GENERAL",
                                "narrativePerspective",
                                "THIRD_PERSON",
                                "lengthType",
                                "LONG_NOVEL",
                                "premise",
                                "A hero returns to the gate after a failed attempt.",
                                "worldRules",
                                List.of()),
                        201))
                .get("id")
                .asString();

        JsonNode result = json(request(
                "POST",
                "/api/projects/" + projectId + "/ai/extractor",
                token,
                Map.of("instruction", "Extract facts", "context", "The hero opens the gate."),
                200));
        assertThat(result.get("events").get(0).asString()).isEqualTo("The gate opens");
        assertThat(jdbcTemplate.queryForObject(
                        "select attempts from usage_record where project_id=?::uuid and agent='EXTRACTOR'",
                        Integer.class,
                        projectId))
                .isEqualTo(3);
        DEEPSEEK.verify(3, postRequestedFor(urlEqualTo("/chat/completions")));

        DEEPSEEK.resetAll();
        DEEPSEEK.stubFor(
                post(urlEqualTo("/chat/completions")).willReturn(aResponse().withStatus(401)));
        HttpResponse<String> unauthorizedUpstream = request(
                "POST",
                "/api/projects/" + projectId + "/ai/reviewer",
                token,
                Map.of("instruction", "Review", "context", "Draft"),
                502);
        assertThat(unauthorizedUpstream.body()).contains("deepseek_http_401");
        DEEPSEEK.verify(1, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    private void configurePlannerRecovery() throws Exception {
        DEEPSEEK.stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("false")))
                .inScenario("empty-json-recovery")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("recovered")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(completion("   ", "private reasoning", "stop", 2, 0, 0, 0, 0))));
        Map<String, Object> plan = Map.of(
                "chapterTitle", "The Gate",
                "chapterGoal", "Enter the city",
                "viewpointCharacterId", "hero",
                "scenes",
                        List.of(Map.of(
                                "title",
                                "Arrival",
                                "goal",
                                "Open the gate",
                                "summary",
                                "The gate opens",
                                "mustInclude",
                                List.of("gate"),
                                "mustAvoid",
                                List.of("teleportation"))),
                "mustInclude", List.of("gate"),
                "mustAvoid", List.of("teleportation"),
                "exitHook", "A bell rings");
        DEEPSEEK.stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("false")))
                .inScenario("empty-json-recovery")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(completion(
                                objectMapper.writeValueAsString(plan),
                                "private reasoning",
                                "stop",
                                20,
                                10,
                                4,
                                7,
                                13))));
    }

    private void configureWriterStream() {
        String body = ": vendor-keep-alive\n\n"
                + "data:\n\n"
                + "data: {\"id\":\"writer-1\",\"model\":\"deepseek-v4-pro\",\"choices\":[{\"delta\":{\"content\":\"Hel\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"id\":\"writer-1\",\"model\":\"deepseek-v4-pro\",\"choices\":[{\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2,\"prompt_cache_hit_tokens\":1,\"prompt_cache_miss_tokens\":4}}\n\n"
                + "data: [DONE]\n\n";
        DEEPSEEK.stubFor(post(urlEqualTo("/chat/completions"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(body)));
    }

    private String completion(
            String content,
            String reasoning,
            String finishReason,
            int prompt,
            int completion,
            int reasoningTokens,
            int hit,
            int miss)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "id",
                "planner-1",
                "model",
                "deepseek-v4-pro",
                "choices",
                List.of(Map.of(
                        "message",
                        Map.of("content", content, "reasoning_content", reasoning, "role", "assistant"),
                        "finish_reason",
                        finishReason,
                        "index",
                        0)),
                "usage",
                Map.of(
                        "prompt_tokens", prompt,
                        "completion_tokens", completion,
                        "prompt_cache_hit_tokens", hit,
                        "prompt_cache_miss_tokens", miss,
                        "completion_tokens_details", Map.of("reasoning_tokens", reasoningTokens))));
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

    private HttpResponse<String> get(String path, String token) throws Exception {
        return request("GET", path, token, null, 200);
    }

    private HttpResponse<String> request(String method, String path, String token, Object body, int expectedStatus)
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
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response;
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException exception) {
            throw exception;
        }
    }
}

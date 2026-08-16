package com.storyweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InfrastructureIT {

    private static final String EXPECTED_API_ROUTES =
            """
            POST /api/auth/register
            POST /api/auth/login
            GET /api/me
            POST /api/projects
            GET /api/projects
            GET /api/projects/{projectId}
            PUT /api/projects/{projectId}
            DELETE /api/projects/{projectId}
            POST /api/projects/{projectId}/snapshots
            POST /api/projects/{projectId}/assets
            GET /api/projects/{projectId}/assets
            PUT /api/assets/{assetId}
            POST /api/assets/{assetId}/confirm
            POST /api/assets/{assetId}/deprecate
            POST /api/projects/{projectId}/characters
            GET /api/projects/{projectId}/characters
            GET /api/characters/{characterId}
            PUT /api/characters/{characterId}
            GET /api/characters/{characterId}/state
            PUT /api/characters/{characterId}/state
            GET /api/characters/{characterId}/state-at
            POST /api/characters/{characterId}/lifecycle
            POST /api/characters/{characterId}/merge
            POST /api/characters/{characterId}/purge
            POST /api/projects/{projectId}/outlines
            GET /api/projects/{projectId}/outlines
            GET /api/outlines/{outlineId}
            PUT /api/outlines/{outlineId}
            POST /api/projects/{projectId}/chapters
            GET /api/projects/{projectId}/chapters
            GET /api/chapters/{chapterId}
            PUT /api/chapters/{chapterId}/outline
            POST /api/chapters/{chapterId}/versions
            GET /api/chapters/{chapterId}/versions
            POST /api/chapters/{chapterId}/restore/{versionNo}
            POST /api/projects/{projectId}/skills
            GET /api/projects/{projectId}/skills
            PUT /api/skills/{skillId}
            POST /api/projects/{projectId}/skills/compose
            GET /api/skills
            POST /api/skills
            GET /api/skills/{skillId}
            DELETE /api/skills/{skillId}
            GET /api/skills/{skillId}/versions
            POST /api/skills/{skillId}/versions
            POST /api/skills/{skillId}/validate
            GET /api/skills/{skillId}/tests
            GET /api/skills/{skillId}/export
            POST /api/skill-forge/runs
            GET /api/skill-forge/runs/{runId}
            GET /api/skill-forge/runs/{runId}/events
            POST /api/skill-forge/runs/{runId}/sources/text
            POST /api/skill-forge/runs/{runId}/sources/txt
            GET /api/skill-forge/runs/{runId}/sources
            DELETE /api/skill-forge/runs/{runId}/sources/{sourceId}
            POST /api/skill-forge/runs/{runId}/start
            GET /api/skill-forge/runs/{runId}/rules
            PATCH /api/skill-forge/runs/{runId}/rules/{ruleId}
            POST /api/skill-forge/runs/{runId}/resolve-conflicts
            POST /api/skill-forge/runs/{runId}/generate-contract
            POST /api/skill-forge/runs/{runId}/validate
            POST /api/skill-forge/runs/{runId}/cancel
            GET /api/projects/{projectId}/skill-bindings
            POST /api/projects/{projectId}/skill-bindings/foundation
            DELETE /api/projects/{projectId}/skill-bindings/foundation
            POST /api/projects/{projectId}/ai/planner
            POST /api/projects/{projectId}/ai/writer
            POST /api/projects/{projectId}/ai/extractor
            POST /api/projects/{projectId}/ai/reviewer
            GET /api/ai/model-config
            POST /api/projects/{projectId}/worldbook-entries
            GET /api/projects/{projectId}/worldbook-entries
            PUT /api/worldbook-entries/{entryId}
            DELETE /api/worldbook-entries/{entryId}
            POST /api/projects/{projectId}/worldbook/preview
            POST /api/projects/{projectId}/story-events
            GET /api/projects/{projectId}/story-events
            PUT /api/story-events/{eventId}
            POST /api/projects/{projectId}/story-events/search
            POST /api/chapters/{chapterId}/workflows
            GET /api/workflows/{runId}
            GET /api/workflows/{runId}/events
            POST /api/workflows/{runId}/cancel
            POST /api/workflows/{runId}/approve
            POST /api/workflows/{runId}/request-revision
            POST /api/workflows/{runId}/reextract
            GET /api/projects/{projectId}/story-facts
            GET /api/projects/{projectId}/item-ownership
            GET /api/characters/{characterId}/knowledge
            GET /api/projects/{projectId}/costs
            GET /api/projects/{projectId}/budget
            PUT /api/projects/{projectId}/budget
            GET /api/pricing-rules
            GET /api/projects/{projectId}/usage
            GET /api/projects/{projectId}/mcp-audit
            POST /api/projects/{projectId}/imports
            GET /api/projects/{projectId}/imports
            GET /api/imports/{importId}
            PUT /api/imports/{importId}/chapters
            POST /api/imports/{importId}/extract
            POST /api/imports/{importId}/retry
            POST /api/imports/{importId}/cancel
            POST /api/imports/{importId}/complete
            POST /api/imports/{importId}/candidates/decide
            POST /api/imports/{importId}/aliases/merge
            GET /api/projects/{projectId}/exports/git
            POST /api/imports/txt
            GET /api/txt-imports/{importId}
            POST /api/txt-imports/{importId}/parse
            GET /api/txt-imports/{importId}/preview
            GET /api/txt-imports/{importId}/chapters/{chapterId}/content
            PATCH /api/txt-imports/{importId}/chapters/{chapterId}
            POST /api/txt-imports/{importId}/chapters/reorder
            POST /api/txt-imports/{importId}/chapters/merge
            POST /api/txt-imports/{importId}/chapters/split
            POST /api/txt-imports/{importId}/chapters/whole
            POST /api/txt-imports/{importId}/chapters/fixed-split
            POST /api/txt-imports/{importId}/commit
            POST /api/txt-imports/{importId}/cancel
            POST /api/projects/{projectId}/book-analysis
            GET /api/txt-imports/{importId}/analysis
            PATCH /api/txt-imports/{importId}/analysis/candidates/{candidateId}
            POST /api/projects/{projectId}/reconstruction/estimate
            POST /api/projects/{projectId}/reconstruction
            GET /api/projects/{projectId}/reconstruction
            POST /api/projects/{projectId}/reconstruction/pause
            POST /api/projects/{projectId}/reconstruction/resume
            POST /api/projects/{projectId}/reconstruction/cancel
            POST /api/projects/{projectId}/reconstruction/retry-failed
            GET /api/projects/{projectId}/reconstruction/candidates
            PATCH /api/projects/{projectId}/reconstruction/candidates/{candidateId}
            POST /api/projects/{projectId}/reconstruction/candidates/{candidateId}/restore
            POST /api/projects/{projectId}/reconstruction/candidates/{candidateId}/revoke
            POST /api/projects/{projectId}/reconstruction/approve-safe
            POST /api/projects/{projectId}/foreshadows
            GET /api/projects/{projectId}/foreshadows
            PUT /api/foreshadows/{id}
            POST /api/foreshadows/{id}/transition
            DELETE /api/foreshadows/{id}
            POST /api/chapters/{chapterId}/impact-reports
            GET /api/chapters/{chapterId}/impact-reports
            GET /api/impact-reports/{id}
            GET /api/projects/{projectId}/rolling-outline
            PUT /api/projects/{projectId}/rolling-outline
            POST /api/projects/{projectId}/rolling-outline/advance
            POST /api/projects/{projectId}/chapter-batches
            GET /api/projects/{projectId}/chapter-batches
            GET /api/chapter-batches/{id}
            POST /api/chapter-batches/{id}/pause
            POST /api/chapter-batches/{id}/resume
            POST /api/chapter-batches/{id}/cancel
            GET /api/chapter-batches/{id}/gates
            POST /api/story-gates/{id}/approve
            POST /api/story-gates/{id}/reject
            POST /api/workflows/{runId}/local-revisions
            POST /api/chapters/{chapterId}/branches
            GET /api/chapters/{chapterId}/branches
            GET /api/chapter-branches/{id}
            POST /api/chapter-branches/{id}/versions
            POST /api/chapter-branches/{id}/promote-impact
            GET /api/workflows/{runId}/model-attempts
            GET /api/ai/model-health
            """;

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
        registry.add("management.endpoint.health.show-details", () -> "always");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @Autowired
    Flyway flyway;

    @Autowired
    @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping requestMappings;

    @Value("${local.server.port}")
    int port;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void startsAgainstSupportedInfrastructureAndPublishesActuatorEndpoints() throws Exception {
        Integer postgresMajor = jdbcTemplate.queryForObject(
                "select current_setting('server_version_num')::integer / 10000", Integer.class);
        assertThat(postgresMajor).isEqualTo(18);

        String redisVersion;
        try (var connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
            redisVersion = connection.serverCommands().info("server").getProperty("redis_version");
        }
        assertThat(redisVersion).startsWith("8.2");
        flyway.validate();
        assertThat(flyway.info().applied()).hasSize(22);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");

        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> health = get(client, "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");

        HttpResponse<String> metrics = get(client, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("jvm_info");

        HttpResponse<String> info = get(client, "/actuator/info");
        assertThat(info.statusCode()).isEqualTo(200);
        assertThat(info.body()).contains("\"phase\":1.5");

        HttpResponse<String> problem = get(client, "/api/auth/not-implemented");
        assertThat(problem.statusCode()).isEqualTo(404);
        assertThat(problem.headers().firstValue("content-type").orElse("")).startsWith("application/problem+json");
    }

    @Test
    void exposesTheDocumentedRestApiSurfaceWithoutUndocumentedBusinessRoutes() {
        Set<String> expected = EXPECTED_API_ROUTES
                .lines()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        Set<String> actual = requestMappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> isStoryWeaverController(entry.getValue()))
                .flatMap(entry -> routes(entry.getKey()).stream())
                .collect(Collectors.toSet());

        assertThat(expected).hasSize(162);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    private boolean isStoryWeaverController(HandlerMethod handler) {
        Package controllerPackage = handler.getBeanType().getPackage();
        return controllerPackage != null
                && controllerPackage.getName().startsWith("com.storyweaver")
                && handler.getBeanType().getSimpleName().endsWith("Controller");
    }

    private Set<String> routes(RequestMappingInfo mapping) {
        return mapping.getMethodsCondition().getMethods().stream()
                .flatMap(method -> mapping.getPatternValues().stream().map(path -> route(method, path)))
                .collect(Collectors.toSet());
    }

    private String route(RequestMethod method, String path) {
        return method.name() + " " + path;
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

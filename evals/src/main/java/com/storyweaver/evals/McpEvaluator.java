package com.storyweaver.evals;

import com.storyweaver.mcp.application.McpStoryService;
import com.storyweaver.mcp.transport.McpCurrentUser;
import com.storyweaver.mcp.transport.StoryMcpCapabilities;
import com.storyweaver.shared.error.NotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import tools.jackson.databind.JsonNode;

final class McpEvaluator {
    private static final UUID USER_ID = stable("eval-user-v1");
    private static final UUID PROJECT_ID = stable("eval-project-v1");
    private static final UUID CHARACTER_ID = stable("eval-character-v1");

    private final Path repoRoot;
    private final String datasetVersion;

    McpEvaluator(Path repoRoot, String datasetVersion) {
        this.repoRoot = repoRoot;
        this.datasetVersion = datasetVersion;
    }

    Map<String, Object> evaluate() throws Exception {
        List<JsonNode> cases = EvalSupport.readJsonl(
                repoRoot.resolve("evals/datasets/mcp/mcp_cases.jsonl"), datasetVersion);
        FixtureMcpStoryService story = new FixtureMcpStoryService();
        McpCurrentUser currentUser = new McpCurrentUser() {
            @Override
            public UUID id() {
                return USER_ID;
            }
        };
        StoryMcpCapabilities capabilities = new StoryMcpCapabilities(story, currentUser);
        Map<String, Method> discovered = discover();
        Set<String> coveredTools = new LinkedHashSet<>();
        List<Map<String, Object>> results = new ArrayList<>();
        long validTotal = 0, validPassed = 0;
        long invalidTotal = 0, invalidPassed = 0;
        long authTotal = 0, authPassed = 0;
        long outputTotal = 0, outputPassed = 0;

        for (JsonNode value : cases) {
            long started = System.nanoTime();
            String caseType = value.path("input").path("caseType").asText();
            String toolName = value.path("input").path("tool").asText();
            JsonNode arguments = value.path("input").path("arguments");
            Method method = discovered.get(toolName);
            boolean passed = false;
            boolean schemaPassed = false;
            Object output = null;
            String errorCode = null;
            int writesBefore = story.mutationCount();
            int candidatesBefore = story.candidateCount();
            String canonBefore = story.canonSnapshot();
            try {
                if (method == null) throw new ToolCallException("tool_not_found", "Unknown tool " + toolName);
                coveredTools.add(toolName);
                Object[] converted = convertArguments(method, arguments);
                output = method.invoke(capabilities, converted);
                schemaPassed = outputSchema(method, output);
                passed = switch (caseType) {
                    case "VALID_CALL" -> schemaPassed;
                    case "READ_ONLY" -> method.getAnnotation(McpTool.class).annotations().readOnlyHint()
                            && story.mutationCount() == writesBefore;
                    case "CANDIDATE_WRITE" -> !method.getAnnotation(McpTool.class).annotations().readOnlyHint()
                            && story.candidateCount() == candidatesBefore + 1
                            && story.canonSnapshot().equals(canonBefore)
                            && output instanceof Map<?, ?> map
                            && Boolean.FALSE.equals(map.get("canonEffect"));
                    default -> false;
                };
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                errorCode = cause instanceof com.storyweaver.shared.error.ApiException api
                        ? api.getCode()
                        : "mcp_invocation_failed";
                passed = ("FORBIDDEN".equals(caseType) && "project_not_found".equals(errorCode))
                        || ("NOT_FOUND".equals(caseType)
                                && value.path("expected").path("errorCode").asText().equals(errorCode));
            } catch (ToolCallException exception) {
                errorCode = exception.code;
                passed = switch (caseType) {
                    case "INVALID_ARGUMENT" -> "invalid_argument".equals(errorCode);
                    case "NOT_FOUND" -> value.path("expected").path("errorCode").asText().equals(errorCode);
                    default -> false;
                };
            } catch (ReflectiveOperationException | RuntimeException exception) {
                errorCode = "mcp_invocation_failed";
            }
            long latency = (System.nanoTime() - started) / 1_000_000;
            if ("VALID_CALL".equals(caseType) || "READ_ONLY".equals(caseType) || "CANDIDATE_WRITE".equals(caseType)) {
                validTotal++;
                if (passed) validPassed++;
                if (output != null) {
                    outputTotal++;
                    if (schemaPassed) outputPassed++;
                }
            }
            if ("INVALID_ARGUMENT".equals(caseType) || "NOT_FOUND".equals(caseType)) {
                invalidTotal++;
                if (passed) invalidPassed++;
            }
            if ("FORBIDDEN".equals(caseType)) {
                authTotal++;
                if (passed) authPassed++;
            }

            Map<String, Object> actual = new LinkedHashMap<>();
            actual.put("toolDiscovered", method != null);
            actual.put("inputSchemaAccepted", output != null);
            actual.put("outputSchemaPassed", output == null ? null : schemaPassed);
            actual.put("errorCode", errorCode);
            actual.put("sideEffectDelta", story.mutationCount() - writesBefore);
            actual.put("candidateDelta", story.candidateCount() - candidatesBefore);
            actual.put("canonChanged", !story.canonSnapshot().equals(canonBefore));
            results.add(EvalSupport.caseResult(
                    value,
                    passed,
                    actual,
                    latency,
                    passed ? null : errorCode,
                    Map.of(
                            "evaluationType", "DETERMINISTIC",
                            "discovery", "StoryMcpCapabilities annotations",
                            "transport", "IN_PROCESS_CONTRACT")));
        }

        Set<String> uncovered = new LinkedHashSet<>(discovered.keySet());
        uncovered.removeAll(coveredTools);
        Map<String, Object> metrics = new LinkedHashMap<>();
        long passedCases = results.stream().filter(item -> Boolean.TRUE.equals(item.get("passed"))).count();
        metrics.put("mcpToolSuccessRate", Metrics.ratio(passedCases, cases.size()));
        metrics.put("validInvocationSuccessRate", Metrics.ratio(validPassed, validTotal));
        metrics.put("invalidInputRejectionRate", Metrics.ratio(invalidPassed, invalidTotal));
        metrics.put("authorizationEnforcementRate", Metrics.ratio(authPassed, authTotal));
        metrics.put("outputSchemaPassRate", Metrics.ratio(outputPassed, outputTotal));
        metrics.put("toolSelectionAccuracy", null);
        metrics.put("toolArgumentAccuracy", null);
        metrics.put("discoveredTools", discovered.keySet());
        metrics.put("uncoveredDiscoveredTools", uncovered);

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("datasetVersion", datasetVersion);
        section.put("evaluationType", "DETERMINISTIC");
        section.put("caseCount", cases.size());
        section.put("metrics", metrics);
        section.put("failedCaseCount", cases.size() - passedCases);
        section.put("cases", results);
        return section;
    }

    private Map<String, Method> discover() {
        Map<String, Method> result = new LinkedHashMap<>();
        Arrays.stream(StoryMcpCapabilities.class.getMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .sorted(java.util.Comparator.comparing(method -> method.getAnnotation(McpTool.class).name()))
                .forEach(method -> result.put(method.getAnnotation(McpTool.class).name(), method));
        return result;
    }

    private Object[] convertArguments(Method method, JsonNode arguments) {
        if (!arguments.isObject()) throw new ToolCallException("invalid_argument", "arguments must be an object");
        Set<String> expectedNames = new LinkedHashSet<>();
        Object[] converted = new Object[method.getParameterCount()];
        for (int index = 0; index < method.getParameterCount(); index++) {
            Parameter parameter = method.getParameters()[index];
            String name = parameter.getName();
            expectedNames.add(name);
            McpToolParam schema = parameter.getAnnotation(McpToolParam.class);
            JsonNode value = arguments.path(name);
            if ((value.isMissingNode() || value.isNull()) && schema != null && schema.required()) {
                throw new ToolCallException("invalid_argument", "Missing required argument " + name);
            }
            if (value.isMissingNode() || value.isNull()) {
                converted[index] = null;
                continue;
            }
            try {
                if (parameter.getType() == UUID.class) converted[index] = UUID.fromString(value.asText());
                else if (parameter.getType() == Integer.class) {
                    if (!value.isIntegralNumber()) throw new IllegalArgumentException("not integer");
                    converted[index] = value.asInt();
                } else if (parameter.getType() == String.class) converted[index] = value.asText();
                else throw new IllegalArgumentException("unsupported parameter type");
            } catch (RuntimeException exception) {
                throw new ToolCallException("invalid_argument", "Invalid argument " + name);
            }
        }
        arguments.propertyNames().forEach(name -> {
            if (!expectedNames.contains(name)) throw new ToolCallException("invalid_argument", "Unexpected argument " + name);
        });
        return converted;
    }

    private boolean outputSchema(Method method, Object output) {
        if (output == null) return false;
        if (Map.class.isAssignableFrom(method.getReturnType())) return output instanceof Map<?, ?>;
        return method.getReturnType() == Object.class || method.getReturnType().isInstance(output);
    }

    static UUID projectId() {
        return PROJECT_ID;
    }

    static UUID characterId() {
        return CHARACTER_ID;
    }

    private static UUID stable(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class ToolCallException extends RuntimeException {
        private final String code;

        private ToolCallException(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static final class FixtureMcpStoryService extends McpStoryService {
        private int mutations;
        private int candidates;
        private final String canon = "confirmed-canon-v1";

        private FixtureMcpStoryService() {
            super(null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public Map<String, Object> characterState(UUID userId, UUID characterId) {
            requireCharacter(characterId);
            return Map.of("characterId", characterId, "lifeStatus", "ALIVE", "version", 1);
        }

        @Override
        public Object characterKnowledge(UUID userId, UUID characterId) {
            requireCharacter(characterId);
            return List.of(Map.of("factKey", "beacon-code", "certainty", "CONFIRMED"));
        }

        @Override
        public Object worldbookEntries(UUID userId, UUID projectId) {
            requireProject(projectId);
            return List.of(Map.of("id", stable("asset-world-rule-001"), "title", "潮汐门规则"));
        }

        @Override
        public Object recentEvents(UUID userId, UUID projectId, Integer limit) {
            requireProject(projectId);
            int safe = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
            return List.of(Map.of("event", "灯塔熄灭", "limit", safe));
        }

        @Override
        public Object itemOwner(UUID userId, UUID projectId, String itemKey) {
            requireProject(projectId);
            return Map.of("itemKey", itemKey, "status", "ACTIVE", "owner", CHARACTER_ID);
        }

        @Override
        public Map<String, Object> saveCandidate(
                UUID userId,
                UUID projectId,
                String factKey,
                String content,
                String evidence,
                String paragraphKey,
                String requestKey) {
            requireProject(projectId);
            if (content == null || content.isBlank() || evidence == null || evidence.isBlank()) {
                throw new IllegalArgumentException("candidate requires content and evidence");
            }
            mutations++;
            candidates++;
            return Map.of(
                    "id", stable("candidate-" + candidates),
                    "projectId", projectId,
                    "factKey", factKey == null ? "generated-" + candidates : factKey,
                    "status", "CANDIDATE",
                    "canonEffect", false,
                    "evidence", evidence);
        }

        int mutationCount() {
            return mutations;
        }

        int candidateCount() {
            return candidates;
        }

        String canonSnapshot() {
            return canon;
        }

        private void requireProject(UUID projectId) {
            if (!PROJECT_ID.equals(projectId)) throw new NotFoundException("project_not_found", "Project was not found");
        }

        private void requireCharacter(UUID characterId) {
            if (!CHARACTER_ID.equals(characterId)) {
                throw new NotFoundException("character_not_found", "Character was not found");
            }
        }
    }
}

package com.storyweaver.evals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class EvalSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(sk-[a-z0-9_-]{12,}|bearer\\s+[a-z0-9._-]{12,}|api[_-]?key\\s*[:=]\\s*[^\\s,]+|jwt\\s*[:=]\\s*[^\\s,]+)");

    private EvalSupport() {}

    static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("evals")) && Files.isDirectory(current.resolve("backend"))) {
            return current;
        }
        if (current.getFileName() != null && current.getFileName().toString().equalsIgnoreCase("evals")) {
            return current.getParent();
        }
        throw new IllegalStateException("Cannot locate StoryWeaver repository root from " + current);
    }

    static List<JsonNode> readJsonl(Path path, String datasetVersion) throws IOException {
        List<JsonNode> values = new ArrayList<>();
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            int[] lineNo = {0};
            lines.forEach(line -> {
                lineNo[0]++;
                if (line.isBlank() || line.stripLeading().startsWith("#")) return;
                JsonNode value;
                try {
                    value = JSON.readTree(line);
                } catch (RuntimeException exception) {
                    throw new DatasetException(path + ":" + lineNo[0] + " invalid JSON", exception);
                }
                validateCase(value, path, lineNo[0], datasetVersion);
                values.add(value);
            });
        } catch (DatasetException exception) {
            throw new IOException(exception.getMessage(), exception.getCause());
        }
        return List.copyOf(values);
    }

    static void validateCase(JsonNode value, Path path, int line, String datasetVersion) {
        requireText(value, "datasetVersion", path, line);
        if (!datasetVersion.equals(value.path("datasetVersion").asText())) {
            throw new DatasetException(path + ":" + line + " datasetVersion mismatch", null);
        }
        for (String field : List.of("caseId", "category", "description", "fixtureProject", "createdBy")) {
            requireText(value, field, path, line);
        }
        if (!value.path("input").isObject() || !value.path("expected").isObject()) {
            throw new DatasetException(path + ":" + line + " input/expected must be objects", null);
        }
        if (!value.path("tags").isArray() || value.path("version").asInt(0) < 1) {
            throw new DatasetException(path + ":" + line + " tags/version are invalid", null);
        }
    }

    private static void requireText(JsonNode value, String field, Path path, int line) {
        if (!value.path(field).isTextual() || value.path(field).asText().isBlank()) {
            throw new DatasetException(path + ":" + line + " missing " + field, null);
        }
    }

    static Map<String, Object> caseResult(
            JsonNode value,
            boolean passed,
            Object actual,
            long latencyMs,
            String error,
            Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", value.path("caseId").asText());
        result.put("passed", passed);
        result.put("actual", actual);
        result.put("expected", JSON.convertValue(value.path("expected"), Object.class));
        result.put("latencyMs", latencyMs);
        result.put("tokenUsage", null);
        result.put("cost", null);
        result.put("error", error == null ? null : redact(error));
        result.put("metadata", metadata);
        return result;
    }

    static void writeJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    static String redact(String value) {
        return value == null ? null : SECRET.matcher(value).replaceAll("[REDACTED]");
    }

    static String percentage(Object value) {
        if (!(value instanceof Number number)) return "—";
        return String.format(Locale.ROOT, "%.2f%%", number.doubleValue() * 100.0);
    }

    static Map<String, Object> environment() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("os", System.getProperty("os.name"));
        result.put("osVersion", System.getProperty("os.version"));
        result.put("java", System.getProperty("java.version"));
        result.put("timezone", java.time.ZoneId.systemDefault().toString());
        return result;
    }

    static String now() {
        return Instant.now().toString();
    }

    private static final class DatasetException extends RuntimeException {
        private DatasetException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

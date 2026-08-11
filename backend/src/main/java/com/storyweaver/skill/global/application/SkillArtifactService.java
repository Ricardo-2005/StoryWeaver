package com.storyweaver.skill.global.application;

import com.storyweaver.skill.global.domain.GlobalSkill;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Builds portable Skill artifacts without including private source text. */
@Service
public class SkillArtifactService {
    private final GlobalSkillService skills;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SkillArtifactService(GlobalSkillService skills, JdbcTemplate jdbc, ObjectMapper json) {
        this.skills = skills;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<TestCaseView> tests(UUID skillId, UUID ownerId) {
        skills.get(skillId, ownerId);
        return jdbc.query(
                """
                SELECT tc.id,tc.case_type,tc.title,tc.prompt,CAST(tc.expected_assertions AS text),
                       latest.status,latest.score,result.passed,result.finding
                FROM skill_test_case tc
                LEFT JOIN LATERAL (
                    SELECT id,status,score FROM skill_test_run
                    WHERE global_skill_id=tc.global_skill_id
                    ORDER BY created_at DESC LIMIT 1
                ) latest ON TRUE
                LEFT JOIN skill_test_result result
                    ON result.test_run_id=latest.id AND result.test_case_id=tc.id
                WHERE tc.global_skill_id=?
                ORDER BY tc.created_at,tc.id
                """,
                (resultSet, rowNumber) -> testCase(resultSet),
                skillId);
    }

    @Transactional(readOnly = true)
    public ExportArtifact export(UUID skillId, UUID ownerId) {
        GlobalSkill skill = skills.get(skillId, ownerId);
        List<TestCaseView> testCases = tests(skillId, ownerId);
        String root = skill.getSlug() + "/";
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            add(zip, root + "SKILL.md", skillMarkdown(skill));
            add(
                    zip,
                    root + "references/narrative-models.md",
                    reference("叙事模型", skill.getContract().get("narrativeModels")));
            add(
                    zip,
                    root + "references/decision-heuristics.md",
                    reference("决策启发式", skill.getContract().get("decisionHeuristics")));
            add(
                    zip,
                    root + "references/expression-dna.md",
                    reference("表达 DNA", skill.getContract().get("expressionDNA")));
            add(
                    zip,
                    root + "references/pacing-patterns.md",
                    reference("节奏模式", skill.getContract().get("pacingPatterns")));
            add(
                    zip,
                    root + "references/anti-patterns.md",
                    reference("反模式", skill.getContract().get("antiPatterns")));
            add(
                    zip,
                    root + "references/boundaries.md",
                    reference("诚实边界", skill.getContract().get("honestyBoundaries")));
            add(
                    zip,
                    root + "tests/test-cases.json",
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(testCases));
            add(
                    zip,
                    root + "LICENSE",
                    "Private Skill artifact. The owner is responsible for source rights and downstream use.\n");
            zip.finish();
            return new ExportArtifact(skill.getSlug() + ".zip", bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not build Skill export", exception);
        }
    }

    private TestCaseView testCase(ResultSet resultSet) throws SQLException {
        String assertions = resultSet.getString(5);
        Object parsedAssertions;
        try {
            parsedAssertions = json.readValue(assertions, Object.class);
        } catch (RuntimeException exception) {
            parsedAssertions = List.of();
        }
        Boolean passed = resultSet.getObject(8, Boolean.class);
        TestResultView result = resultSet.getString(6) == null
                ? null
                : new TestResultView(resultSet.getString(6), resultSet.getInt(7), passed, resultSet.getString(9));
        return new TestCaseView(
                resultSet.getObject(1, UUID.class),
                resultSet.getString(2),
                resultSet.getString(3),
                resultSet.getString(4),
                parsedAssertions,
                result);
    }

    private String skillMarkdown(GlobalSkill skill) throws IOException {
        String description = skill.getDescription().replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                ---
                name: %s
                description: "%s"
                license: Private
                compatibility: StoryWeaver Skill Contract V1.2
                metadata:
                  source: storyweaver-text-evidence-forge
                  raw-sources-included: false
                ---

                # %s

                本 Skill 是经过用户逐条审阅和验证的行为契约。完整结构化契约如下：

                ```json
                %s
                ```

                证据派生规则按主题位于 `references/`，验证用例位于 `tests/`。导出包不包含上传或粘贴的原始文本。
                """
                .formatted(
                        skill.getSlug(),
                        description,
                        skill.getDisplayName(),
                        json.writerWithDefaultPrettyPrinter().writeValueAsString(skill.getContract()));
    }

    private String reference(String title, Object value) throws IOException {
        return "# " + title + "\n\n```json\n"
                + json.writerWithDefaultPrettyPrinter().writeValueAsString(value == null ? List.of() : value)
                + "\n```\n";
    }

    private void add(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    public record TestCaseView(
            UUID id,
            String caseType,
            String title,
            String prompt,
            Object expectedAssertions,
            TestResultView latestResult) {}

    public record TestResultView(String runStatus, int score, Boolean passed, String finding) {}

    public record ExportArtifact(String filename, byte[] content) {}
}

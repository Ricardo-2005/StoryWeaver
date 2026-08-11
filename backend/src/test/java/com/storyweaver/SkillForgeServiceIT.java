package com.storyweaver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyweaver.auth.application.AuthService;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.skill.global.application.SkillArtifactService;
import com.storyweaver.skill.global.application.SkillForgeService;
import com.storyweaver.skill.global.domain.ForgeRunStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class SkillForgeServiceIT {
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
        registry.add("storyweaver.security.jwt.secret", () -> "skill-forge-test-secret-at-least-32-bytes");
        registry.add("storyweaver.skill-forge.max-file-bytes", () -> 1024);
        registry.add("storyweaver.skill-forge.max-total-bytes", () -> 4096);
    }

    @Autowired
    SkillForgeService forge;

    @Autowired
    SkillArtifactService artifacts;

    @Autowired
    AuthService auth;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void preservesTextEvidenceReviewsEveryRuleAndPublishesOnlyValidatedVersion() throws Exception {
        UUID ownerId = auth.register("forge-owner", "forge-owner@example.com", "forge-owner-password")
                .user()
                .getId();
        UUID intruderId = auth.register("forge-intruder", "forge-intruder@example.com", "forge-intruder-password")
                .user()
                .getId();

        assertThatThrownBy(() -> forge.create(
                        ownerId,
                        "rights-missing",
                        "未确认权利",
                        "FOUNDATION",
                        "PROSE",
                        null,
                        null,
                        null,
                        null,
                        true,
                        true,
                        true,
                        true,
                        false,
                        null))
                .isInstanceOf(BadRequestException.class);

        var run = forge.create(
                ownerId,
                "my-evidence-writing",
                "我的证据写作",
                "FOUNDATION",
                "DIALOGUE",
                null,
                null,
                "学习对话节奏，不保留专有剧情",
                "两份独立练习文本",
                true,
                true,
                true,
                true,
                true,
                "我拥有这些文本的分析权利");
        UUID runId = run.getId();
        assertThat(run.getStatus()).isEqualTo(ForgeRunStatus.CREATED);
        assertThatThrownBy(() -> forge.get(runId, intruderId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> forge.addManualSource(runId, ownerId, "太短", "不足二百字", "PROSE", true))
                .isInstanceOf(BadRequestException.class);

        String manual = ("“你为什么回来？”\n“因为门还开着。”\n他放下伞，于是屋内的争执停了一瞬。\n\n" + "人物没有解释过去，只用动作和短回合对话推动眼前冲突。\n").repeat(12);
        forge.addManualSource(runId, ownerId, "手写对话练习", manual, "DIALOGUE", true);

        String rulesText = ("写作规范：不要连续解释同一设定。避免用总结句代替人物决定。\n\n" + "场景必须由人物选择推进，因此每段行动都要留下可观察结果。\n").repeat(8);
        MockMultipartFile gb18030 = new MockMultipartFile(
                "files", "写作规范.txt", "text/plain", rulesText.getBytes(Charset.forName("GB18030")));
        var uploaded = forge.addTxtSources(runId, ownerId, List.of(gb18030), List.of("我的写作规范"), "WRITING_RULES", true);
        assertThat(uploaded).singleElement().satisfies(source -> {
            assertThat(source.detectedEncoding()).isEqualTo("GB18030");
            assertThat(source.contentHash()).hasSize(64);
        });
        assertThat(forge.sources(runId, ownerId)).hasSize(2);

        run = forge.start(runId, ownerId);
        assertThat(run.getStatus()).isEqualTo(ForgeRunStatus.WAITING_REVIEW);
        var candidates = forge.rules(runId, ownerId);
        assertThat(candidates).hasSize(6).allSatisfy(rule -> {
            assertThat(rule.evidence()).isNotEmpty();
            assertThat(rule.evidence().getFirst().get("paragraphKey")).isNotNull();
            assertThat(rule.evidence().getFirst().get("excerptHash").toString()).hasSize(64);
            assertThat(rule.evidence().getFirst().get("excerpt").toString()).isNotBlank();
        });
        assertThat(candidates).anySatisfy(rule -> assertThat(rule.scope()).isEqualTo("EXPLICIT_USER_RULE"));

        for (var rule : candidates) forge.reviewRule(runId, rule.id(), ownerId, "ACCEPT", null);
        run = forge.generateContract(runId, ownerId);
        assertThat(run.getCandidateContract().toString())
                .contains("TEXT_EVIDENCE_FORGE")
                .contains("rawTextIncludedInContract=false")
                .contains("materialTag=DIALOGUE")
                .contains("type=FOUNDATION")
                .contains("使用目标")
                .doesNotContain("你为什么回来");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM skill_test_case WHERE forge_run_id=?", Integer.class, runId))
                .isEqualTo(8);

        var validation = forge.validate(runId, ownerId);
        assertThat(validation.valid()).isTrue();
        assertThat(validation.score()).isEqualTo(100);
        assertThat(forge.get(runId, ownerId).getStatus()).isEqualTo(ForgeRunStatus.VALIDATED);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM global_skill_atomic_rule WHERE forge_run_id=? AND skill_version_id IS NOT NULL",
                        Integer.class,
                        runId))
                .isEqualTo(6);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM skill_test_result r JOIN skill_test_run tr ON tr.id=r.test_run_id WHERE tr.forge_run_id=? AND r.passed",
                        Integer.class,
                        runId))
                .isEqualTo(8);

        UUID skillId = validation.version().getGlobalSkillId();
        assertThat(artifacts.tests(skillId, ownerId)).hasSize(8).allSatisfy(test -> {
            assertThat(test.latestResult()).isNotNull();
            assertThat(test.latestResult().passed()).isTrue();
        });
        var export = artifacts.export(skillId, ownerId);
        assertThat(export.filename()).isEqualTo("my-evidence-writing.zip");
        Set<String> entries = zipEntries(export.content());
        assertThat(entries)
                .contains(
                        "my-evidence-writing/SKILL.md",
                        "my-evidence-writing/references/narrative-models.md",
                        "my-evidence-writing/tests/test-cases.json",
                        "my-evidence-writing/LICENSE")
                .noneMatch(name -> name.contains("sources") || name.endsWith(".txt"));
    }

    @Test
    void rejectsUnsupportedOversizedAndUtf16TxtFiles() {
        UUID ownerId = auth.register("forge-files", "forge-files@example.com", "forge-files-password")
                .user()
                .getId();
        var run = forge.create(
                ownerId,
                "file-boundaries",
                "文件边界",
                "TECHNIQUE",
                "OTHER",
                null,
                null,
                null,
                null,
                true,
                true,
                true,
                true,
                true,
                "我拥有文本权利");

        assertThatThrownBy(() -> forge.addTxtSources(
                        run.getId(),
                        ownerId,
                        List.of(new MockMultipartFile(
                                "files", "payload.exe", "application/octet-stream", "hello".getBytes())),
                        List.of(),
                        "OTHER",
                        true))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> forge.addTxtSources(
                        run.getId(),
                        ownerId,
                        List.of(new MockMultipartFile("files", "large.txt", "text/plain", new byte[1025])),
                        List.of(),
                        "PROSE",
                        true))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> forge.addTxtSources(
                        run.getId(),
                        ownerId,
                        List.of(new MockMultipartFile(
                                "files", "utf16.txt", "text/plain", new byte[] {(byte) 0xFF, (byte) 0xFE, 65, 0})),
                        List.of(),
                        "PROSE",
                        true))
                .isInstanceOf(BadRequestException.class);
    }

    private Set<String> zipEntries(byte[] content) throws Exception {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }
}

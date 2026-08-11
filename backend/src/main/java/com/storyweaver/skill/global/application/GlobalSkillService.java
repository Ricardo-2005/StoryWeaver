package com.storyweaver.skill.global.application;

import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.skill.global.domain.GlobalSkill;
import com.storyweaver.skill.global.domain.GlobalSkillScope;
import com.storyweaver.skill.global.domain.GlobalSkillStatus;
import com.storyweaver.skill.global.domain.GlobalSkillVersion;
import com.storyweaver.skill.global.repository.GlobalSkillRepository;
import com.storyweaver.skill.global.repository.GlobalSkillVersionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalSkillService {
    private static final Set<String> REQUIRED_CONTRACT_SECTIONS = Set.of(
            "identity",
            "scope",
            "inputs",
            "outputs",
            "preconditions",
            "workflow",
            "constraints",
            "antiPatterns",
            "honestyBoundaries",
            "recovery",
            "termination",
            "provenance",
            "evaluation");
    private final GlobalSkillRepository skills;
    private final GlobalSkillVersionRepository versions;
    private final Clock clock;

    public GlobalSkillService(GlobalSkillRepository skills, GlobalSkillVersionRepository versions, Clock clock) {
        this.skills = skills;
        this.versions = versions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GlobalSkill> list(UUID ownerId) {
        return skills.findAllByScopeOrOwnerIdOrderByUpdatedAtDesc(GlobalSkillScope.BUILT_IN, ownerId);
    }

    @Transactional(readOnly = true)
    public GlobalSkill get(UUID skillId, UUID ownerId) {
        return requireVisible(skillId, ownerId);
    }

    @Transactional(readOnly = true)
    public List<GlobalSkillVersion> versions(UUID skillId, UUID ownerId) {
        requireVisible(skillId, ownerId);
        return versions.findAllByGlobalSkillIdOrderByVersionNoDesc(skillId);
    }

    @Transactional
    public GlobalSkill create(
            UUID ownerId, String slug, String displayName, String description, Map<String, Object> contract) {
        String normalizedSlug = normalizeSlug(slug);
        Map<String, Object> normalizedContract = copyContract(contract);
        var now = clock.instant();
        GlobalSkill skill = skills.save(new GlobalSkill(
                ownerId,
                normalizedSlug,
                displayName.trim(),
                description.trim(),
                GlobalSkillScope.PRIVATE_GLOBAL,
                GlobalSkillStatus.DRAFT,
                normalizedContract,
                now));
        GlobalSkillVersion version = versions.save(new GlobalSkillVersion(
                skill.getId(),
                1,
                normalizedContract,
                hash(normalizedContract),
                GlobalSkillStatus.DRAFT,
                estimateTokens(normalizedContract),
                ownerId,
                now));
        skill.publishVersion(version.getId(), normalizedContract, GlobalSkillStatus.DRAFT, now);
        return skill;
    }

    @Transactional
    public GlobalSkillVersion createVersion(UUID skillId, UUID ownerId, Map<String, Object> contract) {
        GlobalSkill skill = requireOwned(skillId, ownerId);
        Map<String, Object> normalizedContract = copyContract(contract);
        int versionNo = versions.findTopByGlobalSkillIdOrderByVersionNoDesc(skillId)
                .map(value -> value.getVersionNo() + 1)
                .orElse(1);
        var now = clock.instant();
        GlobalSkillVersion version = versions.save(new GlobalSkillVersion(
                skillId,
                versionNo,
                normalizedContract,
                hash(normalizedContract),
                GlobalSkillStatus.DRAFT,
                estimateTokens(normalizedContract),
                ownerId,
                now));
        skill.publishVersion(version.getId(), normalizedContract, GlobalSkillStatus.DRAFT, now);
        return version;
    }

    @Transactional
    public ValidationResult validate(UUID skillId, UUID ownerId) {
        GlobalSkill skill = requireOwned(skillId, ownerId);
        Map<String, Object> contract = copyContract(skill.getContract());
        List<String> missing = REQUIRED_CONTRACT_SECTIONS.stream()
                .filter(section -> !contract.containsKey(section))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            skill.publishVersion(
                    skill.getCurrentVersionId(), contract, GlobalSkillStatus.VALIDATION_FAILED, clock.instant());
            return new ValidationResult(false, 0, missing, null);
        }
        int versionNo = versions.findTopByGlobalSkillIdOrderByVersionNoDesc(skillId)
                .map(value -> value.getVersionNo() + 1)
                .orElse(1);
        var now = clock.instant();
        GlobalSkillVersion validated = versions.saveAndFlush(new GlobalSkillVersion(
                skillId,
                versionNo,
                contract,
                hash(contract),
                GlobalSkillStatus.VALIDATED,
                estimateTokens(contract),
                ownerId,
                now));
        skill.publishVersion(validated.getId(), contract, GlobalSkillStatus.VALIDATED, now);
        return new ValidationResult(true, 100, List.of(), validated);
    }

    @Transactional
    public void archive(UUID skillId, UUID ownerId) {
        requireOwned(skillId, ownerId).archive(clock.instant());
    }

    @Transactional
    public void replaceDraftContract(UUID skillId, UUID ownerId, Map<String, Object> contract) {
        requireOwned(skillId, ownerId).replaceDraftContract(copyContract(contract), clock.instant());
    }

    @Transactional(readOnly = true)
    public GlobalSkillVersion requireBindableVersion(UUID versionId, UUID ownerId) {
        GlobalSkillVersion version = versions.findById(versionId)
                .orElseThrow(() -> new NotFoundException("skill_version_not_found", "Skill version was not found"));
        GlobalSkill skill = requireVisible(version.getGlobalSkillId(), ownerId);
        if (skill.getStatus() != GlobalSkillStatus.VALIDATED || version.getStatus() != GlobalSkillStatus.VALIDATED) {
            throw new BadRequestException(
                    "skill_version_not_validated", "Only a validated Skill version can be bound to a project");
        }
        return version;
    }

    public Map<String, Object> defaultContract(String displayName, String material) {
        Map<String, Object> identity = Map.of("displayName", displayName, "type", "FOUNDATION", "version", "0.1.0");
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("identity", identity);
        contract.put("scope", Map.of("useWhen", List.of("章节规划", "场景写作"), "doNotUseWhen", List.of("未授权模仿特定作者")));
        contract.put("inputs", Map.of("required", List.of("authorIntent", "chapterOutline", "canonContext")));
        contract.put("outputs", Map.of("writing", "ChapterDraft", "review", "ReviewFinding"));
        contract.put("preconditions", List.of("正典上下文可用", "视角人物明确"));
        contract.put("workflow", List.of("读取任务", "提取六维候选规则", "执行写作", "检查反模式", "输出不确定项"));
        contract.put("narrativeModels", List.of("人物选择推动情节", "每场改变状态"));
        contract.put("decisionHeuristics", List.of(Map.of("when", "当前视角无法知道信息", "then", "改为可观察证据")));
        contract.put("expressionDNA", Map.of("rules", List.of()));
        contract.put("constraints", List.of("不得覆盖用户确认的项目偏好", "不得直接写入正典事实"));
        contract.put("antiPatterns", List.of("重复解释同一设定", "视角越界"));
        contract.put("honestyBoundaries", List.of("来源不足时标记不确定", "不精确模仿特定在世作者"));
        contract.put("toolPolicy", Map.of("forbidden", List.of("publish-content")));
        contract.put("contextBudget", Map.of("maxInstructionTokens", 5000));
        contract.put("recovery", Map.of("missingContext", "请求补充或降级为候选建议"));
        contract.put("termination", Map.of("success", List.of("输出满足 Schema", "无 BLOCKER")));
        contract.put(
                "provenance",
                Map.of(
                        "generatedBy",
                        "TEXT_EVIDENCE_FORGE",
                        "materialSummary",
                        material.substring(0, Math.min(120, material.length())),
                        "reviewedByUser",
                        false));
        contract.put("evaluation", Map.of("minimumScore", 85));
        return contract;
    }

    private GlobalSkill requireVisible(UUID skillId, UUID ownerId) {
        GlobalSkill skill = skills.findById(skillId)
                .orElseThrow(() -> new NotFoundException("skill_not_found", "Skill was not found"));
        if (skill.getScope() != GlobalSkillScope.BUILT_IN && !ownerId.equals(skill.getOwnerId()))
            throw new NotFoundException("skill_not_found", "Skill was not found");
        return skill;
    }

    private GlobalSkill requireOwned(UUID skillId, UUID ownerId) {
        return skills.findByIdAndOwnerId(skillId, ownerId)
                .orElseThrow(() -> new NotFoundException("skill_not_found", "Skill was not found"));
    }

    private String normalizeSlug(String value) {
        String slug = value == null ? "" : value.trim().toLowerCase();
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new BadRequestException(
                    "invalid_skill_slug", "Skill name must use lowercase letters, numbers and hyphens");
        return slug;
    }

    private Map<String, Object> copyContract(Map<String, Object> contract) {
        if (contract == null || contract.isEmpty())
            throw new BadRequestException("skill_contract_required", "A Skill contract is required");
        return new LinkedHashMap<>(contract);
    }

    private String hash(Map<String, Object> contract) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(contract.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int estimateTokens(Map<String, Object> contract) {
        return Math.max(1, contract.toString().length() / 3);
    }

    public record ValidationResult(
            boolean valid, int score, List<String> missingSections, GlobalSkillVersion version) {}
}

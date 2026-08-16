package com.storyweaver.workflow.application;

import com.storyweaver.chapter.domain.Chapter;
import com.storyweaver.chapter.repository.ChapterRepository;
import com.storyweaver.character.repository.CharacterRepository;
import com.storyweaver.llm.config.DeepSeekProperties;
import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.project.domain.NovelProject;
import com.storyweaver.project.repository.NovelProjectRepository;
import com.storyweaver.skill.application.SkillService;
import com.storyweaver.usage.application.BudgetService;
import com.storyweaver.workflow.domain.WorkflowRun;
import org.springframework.stereotype.Component;

@Component
public class WorkflowPreflight {
    private final NovelProjectRepository projects;
    private final ChapterRepository chapters;
    private final CharacterRepository characters;
    private final SkillService skills;
    private final DeepSeekProperties deepSeek;
    private final BudgetService budgets;

    public WorkflowPreflight(
            NovelProjectRepository projects,
            ChapterRepository chapters,
            CharacterRepository characters,
            SkillService skills,
            DeepSeekProperties deepSeek,
            BudgetService budgets) {
        this.projects = projects;
        this.chapters = chapters;
        this.characters = characters;
        this.skills = skills;
        this.deepSeek = deepSeek;
        this.budgets = budgets;
    }

    public void check(WorkflowRun run) {
        NovelProject project = projects.findByIdAndOwnerId(run.getProjectId(), run.getUserId())
                .orElseThrow(() -> blocked("project_not_found", "Project was not found"));
        if (project.isArchived()) throw blocked("project_archived", "Archived projects cannot start workflows");
        if (project.getAuthorIntent() == null || project.getAuthorIntent().isBlank()) {
            throw blocked("author_intent_required", "Project author intent is required before generation");
        }
        Chapter chapter = chapters.findById(run.getChapterId())
                .filter(candidate -> candidate.getProjectId().equals(run.getProjectId()))
                .orElseThrow(() -> blocked("chapter_not_found", "Chapter was not found"));
        if (chapter.getOutline() == null || chapter.getOutline().isBlank()) {
            throw blocked("chapter_outline_required", "A confirmed chapter outline is required before generation");
        }
        if (characters
                .findById(run.getViewpointCharacterId())
                .filter(character -> character.getProjectId().equals(run.getProjectId())
                        && character.getLifecycleStatus().currentContextEligible())
                .isEmpty()) {
            throw blocked("viewpoint_character_required", "A valid viewpoint character is required");
        }
        if (chapter.getChapterNo() > 1) {
            Chapter previous = chapters.findAllByProjectIdOrderByChapterNoAsc(run.getProjectId()).stream()
                    .filter(candidate -> candidate.getChapterNo() == chapter.getChapterNo() - 1)
                    .findFirst()
                    .orElseThrow(() -> blocked("previous_chapter_missing", "The previous chapter is missing"));
            if (previous.getCurrentVersionNo() == 0) {
                throw blocked("previous_chapter_uncommitted", "The previous chapter must have a committed version");
            }
        }
        var composition = skills.compose(run.getProjectId(), run.getUserId(), run.getChapterId());
        if (!composition.resolved()) {
            throw blocked("skill_conflict", "Skill conflicts must be resolved before generation");
        }
        if (!deepSeek.configured()) {
            throw blocked("deepseek_not_configured", "DeepSeek must be configured before generation");
        }
        try {
            int projectedTokens = DeepSeekAgent.PLANNER.maxOutputTokens()
                    + DeepSeekAgent.WRITER.maxOutputTokens()
                    + DeepSeekAgent.EXTRACTOR.maxOutputTokens()
                    + DeepSeekAgent.REVIEWER.maxOutputTokens();
            budgets.checkWorkflow(
                    run.getProjectId(),
                    run.getUserId(),
                    projectedTokens,
                    DeepSeekAgent.WRITER.maxOutputTokens(),
                    DeepSeekAgent.PLANNER.maxOutputTokens());
        } catch (com.storyweaver.shared.error.ApiException exception) {
            throw blocked(exception.getCode(), exception.getMessage());
        }
    }

    private WorkflowBlockedException blocked(String code, String message) {
        return new WorkflowBlockedException(code, message);
    }
}

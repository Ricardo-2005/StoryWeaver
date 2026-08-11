package com.storyweaver.skill.application;

import com.storyweaver.skill.domain.SkillBinding;
import com.storyweaver.skill.domain.SkillDefinition;
import com.storyweaver.skill.domain.SkillScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SkillComposer {

    public Composition compose(List<BoundSkill> skills) {
        Map<String, EffectiveRule> effective = new TreeMap<>();
        List<RuleConflict> conflicts = new ArrayList<>();

        for (SkillScope scope : List.of(SkillScope.BASE, SkillScope.PROJECT, SkillScope.CHAPTER)) {
            Map<String, List<RuleCandidate>> candidates = new TreeMap<>();
            skills.stream()
                    .filter(skill ->
                            skill.definition().isEnabled() && skill.binding().getScope() == scope)
                    .sorted(Comparator.comparing(skill -> skill.definition().getId()))
                    .forEach(skill -> skill.definition().getRules().forEach((key, value) -> candidates
                            .computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add(new RuleCandidate(
                                    skill.definition().getId(),
                                    skill.definition().getName(),
                                    value))));

            candidates.forEach((key, values) -> {
                LinkedHashSet<String> distinct = new LinkedHashSet<>();
                values.forEach(value -> distinct.add(value.value()));
                if (distinct.size() > 1) {
                    conflicts.add(new RuleConflict(
                            scope,
                            key,
                            List.copyOf(distinct),
                            values.stream().map(RuleCandidate::skillId).toList()));
                } else {
                    RuleCandidate winner = values.get(0);
                    effective.put(
                            key, new EffectiveRule(key, winner.value(), scope, winner.skillId(), winner.skillName()));
                }
            });
        }
        return new Composition(conflicts.isEmpty(), new LinkedHashMap<>(effective), List.copyOf(conflicts));
    }

    public record BoundSkill(SkillDefinition definition, SkillBinding binding) {}

    private record RuleCandidate(UUID skillId, String skillName, String value) {}

    public record EffectiveRule(String key, String value, SkillScope scope, UUID skillId, String skillName) {}

    public record RuleConflict(SkillScope scope, String key, List<String> values, List<UUID> skillIds) {}

    public record Composition(
            boolean resolved, Map<String, EffectiveRule> effectiveRules, List<RuleConflict> conflicts) {}
}

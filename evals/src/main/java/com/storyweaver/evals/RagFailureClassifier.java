package com.storyweaver.evals;

import com.storyweaver.llm.config.RetrievalExperimentMode;
import com.storyweaver.worldbook.application.WorldbookService.ActivationReport;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RagFailureClassifier {
    private RagFailureClassifier() {}

    static RagFailureCategory classify(
            RetrievalExperimentMode mode,
            List<String> retrieved,
            List<String> relevant,
            List<String> required,
            List<ActivationReport> reports,
            Map<UUID, String> logicalIds,
            Map<String, String> assetTypes,
            int k) {
        if (new HashSet<>(retrieved).size() != retrieved.size()) return RagFailureCategory.DUPLICATE_RESULT;
        Set<String> missing = new HashSet<>(required);
        missing.removeAll(retrieved.stream().limit(k).toList());
        if (missing.isEmpty()) {
            missing.addAll(relevant);
            missing.removeAll(retrieved.stream().limit(k).toList());
        }
        if (missing.isEmpty()) return RagFailureCategory.OTHER;
        if (required.size() > 1 && missing.size() > 1) return RagFailureCategory.MULTI_ASSET_MISSING;

        List<ActivationReport> missingReports = reports.stream()
                .filter(report -> missing.contains(logicalIds.get(report.entryId())))
                .toList();
        if (missingReports.stream().anyMatch(report -> "TOKEN_BUDGET".equals(report.dropReason()))) {
            return RagFailureCategory.TOKEN_BUDGET_TRUNCATION;
        }
        if (mode == RetrievalExperimentMode.BASELINE
                && retrieved.stream().limit(k).filter(id -> isConstant(id, reports, logicalIds)).count() >= 2
                && !missingReports.isEmpty()) {
            return RagFailureCategory.CONSTANT_RULE_CROWDING;
        }
        if (missingReports.stream().anyMatch(report -> report.retrievalRank() != null && report.retrievalRank() > k)) {
            return RagFailureCategory.RELEVANT_BUT_LOW_RANK;
        }
        if (missingReports.stream().anyMatch(report -> report.constant() && report.selected())) {
            return RagFailureCategory.RELEVANT_BUT_LOW_RANK;
        }
        if (missingReports.isEmpty()) return RagFailureCategory.TRUE_RETRIEVAL_MISS;
        if (missingReports.stream().noneMatch(report -> report.sources().contains("KEYWORD"))) {
            return RagFailureCategory.KEYWORD_MISMATCH;
        }
        if (missingReports.stream().noneMatch(report -> report.sources().contains("VECTOR"))) {
            return RagFailureCategory.SEMANTIC_MISMATCH;
        }
        String expectedType = missing.stream().map(assetTypes::get).filter(type -> type != null).findFirst().orElse(null);
        if (expectedType != null
                && retrieved.stream().limit(k).map(assetTypes::get).filter(expectedType::equals).count() == 0) {
            return RagFailureCategory.WRONG_ASSET_TYPE;
        }
        return RagFailureCategory.OTHER;
    }

    private static boolean isConstant(
            String logicalId, List<ActivationReport> reports, Map<UUID, String> logicalIds) {
        return reports.stream()
                .anyMatch(report -> report.constant() && logicalId.equals(logicalIds.get(report.entryId())));
    }
}

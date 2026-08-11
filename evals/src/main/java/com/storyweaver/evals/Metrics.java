package com.storyweaver.evals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Metrics {
    private Metrics() {}

    static double ratio(long numerator, long denominator) {
        return denominator == 0 ? Double.NaN : (double) numerator / denominator;
    }

    static double recall(List<String> retrieved, List<String> relevant, int k) {
        if (relevant.isEmpty()) return Double.NaN;
        long hits = retrieved.stream().limit(k).filter(relevant::contains).distinct().count();
        return ratio(hits, relevant.stream().distinct().count());
    }

    static boolean requiredHit(List<String> retrieved, List<String> required, int k) {
        List<String> top = retrieved.stream().limit(k).toList();
        return top.containsAll(required);
    }

    static Integer firstRelevantRank(List<String> retrieved, List<String> relevant) {
        for (int index = 0; index < retrieved.size(); index++) {
            if (relevant.contains(retrieved.get(index))) return index + 1;
        }
        return null;
    }

    static double reciprocalRank(List<String> retrieved, List<String> relevant) {
        Integer rank = firstRelevantRank(retrieved, relevant);
        return rank == null ? 0.0 : 1.0 / rank;
    }

    static double binaryNdcg(List<String> retrieved, List<String> relevant, int k) {
        if (relevant.isEmpty()) return Double.NaN;
        double dcg = 0.0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int index = 0; index < Math.min(k, retrieved.size()); index++) {
            String id = retrieved.get(index);
            if (relevant.contains(id) && seen.add(id)) dcg += 1.0 / log2(index + 2.0);
        }
        double ideal = 0.0;
        int idealHits = Math.min(k, relevant.stream().distinct().toList().size());
        for (int index = 0; index < idealHits; index++) ideal += 1.0 / log2(index + 2.0);
        return ideal == 0.0 ? Double.NaN : dcg / ideal;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    static double reduction(long baseline, long optimized) {
        if (baseline <= 0) return Double.NaN;
        return 1.0 - (double) optimized / baseline;
    }

    static double mean(List<? extends Number> values) {
        if (values.isEmpty()) return Double.NaN;
        return values.stream().mapToDouble(Number::doubleValue).average().orElse(Double.NaN);
    }

    static double percentile(List<? extends Number> values, double percentile) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> sorted = new ArrayList<>(values.stream().map(Number::doubleValue).toList());
        Collections.sort(sorted);
        if (sorted.size() == 1) return sorted.get(0);
        double position = percentile * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted.get(lower);
        double fraction = position - lower;
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * fraction;
    }

    static Classification classification(long tp, long tn, long fp, long fn) {
        double precision = ratio(tp, tp + fp);
        double recall = ratio(tp, tp + fn);
        double f1 = Double.isNaN(precision) || Double.isNaN(recall) || precision + recall == 0
                ? 0.0
                : 2 * precision * recall / (precision + recall);
        return new Classification(tp, tn, fp, fn, ratio(tp + tn, tp + tn + fp + fn), precision, recall, f1);
    }

    record Classification(
            long truePositive,
            long trueNegative,
            long falsePositive,
            long falseNegative,
            double accuracy,
            double precision,
            double recall,
            double f1) {}
}

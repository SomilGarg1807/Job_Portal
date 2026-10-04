package com.somil.jobportal.eval;

import java.util.List;
import java.util.Map;

/** Ranking metrics are computed only when the judged pool is complete. */
final class EvalMetrics {
    private EvalMetrics() { }
    static Result measure(List<String> ranking, Map<String, Integer> grades, int relevantTotal, boolean noAnswer) {
        if (ranking.stream().limit(10).anyMatch(key -> !grades.containsKey(key)))
            throw new IllegalArgumentException("Unjudged result; metrics withheld.");
        long relevant5 = ranking.stream().limit(5).filter(key -> grades.get(key) > 0).count();
        long relevant10 = ranking.stream().limit(10).filter(key -> grades.get(key) > 0).count();
        double reciprocalRank = 0;
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            if (grades.getOrDefault(ranking.get(i), 0) > 0) { reciprocalRank = 1.0 / (i + 1); break; }
        }
        return new Result(relevant5 / 5.0, relevantTotal == 0 ? 0.0 : relevant10 / (double) relevantTotal,
                reciprocalRank, noAnswer && ranking.isEmpty());
    }
    record Result(double precision5, double recall10, double mrr, boolean correctNoAnswer) { }
    static double percentile(List<Long> millis, double percentile) {
        if (millis.isEmpty()) return 0;
        List<Long> sorted = millis.stream().sorted().toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}

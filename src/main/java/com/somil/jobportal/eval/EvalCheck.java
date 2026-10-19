package com.somil.jobportal.eval;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Compares aggregate metrics with the committed baseline, so CI fails when search quality drops.
 * Groups are keyed "mode/strategy/threshold", e.g. "hybrid/max/0.55". Only decreases beyond the
 * tolerance fail; an improvement passes and is a reason to raise the baseline in the same change.
 */
final class EvalCheck {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A failure whose message is safe to print: it never contains URLs, credentials or provider payloads. */
    static final class Failure extends RuntimeException {
        Failure(String message) { super(message); }
    }

    private EvalCheck() { }

    static void verify(Path baselineFile, Map<String, Map<String, Double>> measured) throws Exception {
        JsonNode baseline = JSON.readTree(baselineFile.toFile());
        List<String> problems = regressions(baseline, measured);
        baseline.path("metrics").fieldNames().forEachRemaining(group -> System.out.println("Quality check " + group + ": "
                + format(measured.getOrDefault(group, Map.of()))));
        if (!problems.isEmpty())
            throw new Failure("Search quality regressed (tolerance " + tolerance(baseline) + "): " + String.join("; ", problems)
                    + ". If the change is intended, update " + baselineFile + " in the same commit.");
    }

    static List<String> regressions(JsonNode baseline, Map<String, Map<String, Double>> measured) {
        double tolerance = tolerance(baseline);
        List<String> problems = new ArrayList<>();
        baseline.path("metrics").fields().forEachRemaining(group -> {
            Map<String, Double> actual = measured.get(group.getKey());
            group.getValue().fields().forEachRemaining(metric -> {
                Double value = actual == null ? null : actual.get(metric.getKey());
                double expected = metric.getValue().asDouble();
                if (value == null) problems.add(group.getKey() + " " + metric.getKey() + " was not measured");
                else if (value < expected - tolerance)
                    problems.add(String.format(Locale.ROOT, "%s %s %.3f < baseline %.3f",
                            group.getKey(), metric.getKey(), value, expected));
            });
        });
        return problems;
    }

    private static double tolerance(JsonNode baseline) { return baseline.path("tolerance").asDouble(0.02); }

    private static String format(Map<String, Double> values) {
        List<String> parts = new ArrayList<>();
        values.forEach((name, value) -> parts.add(String.format(Locale.ROOT, "%s=%.3f", name, value)));
        return parts.isEmpty() ? "not measured" : String.join(", ", parts);
    }
}

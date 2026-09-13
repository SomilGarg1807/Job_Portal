package com.somil.jobportal.util;

import com.somil.jobportal.entity.JobPostActivity;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class JobExperience {
    private JobExperience() {}
    private static final Set<String> FILTERS = Set.of("0-1", "1-3", "3-5", "5-8", "8-12", "12+", "unspecified");
    private static final Pattern RANGE = Pattern.compile("(?i)(?<![\\d.])(\\d{1,2})\\s*(?:-|–|—|to)\\s*(\\d{1,2})\\s*(?:years?|yrs?)\\b");
    private static final Pattern SINGLE = Pattern.compile("(?i)(?<![\\d.])(\\d{1,2})\\s*(\\+)?\\s*(?:years?|yrs?)\\s+(?:of\\s+)?(?:(?:relevant|professional|practical|industry)\\s+)?experience\\b");
    public record Range(int min, int max) {}
    public static String normalize(String filter) { return FILTERS.contains(filter == null ? "" : filter) ? filter : ""; }
    public static Range range(JobPostActivity job) {
        if (job.getMinExperienceYears() != null)
            return new Range(job.getMinExperienceYears(), job.getMaxExperienceYears() == null ? 50 : job.getMaxExperienceYears());
        // Compatibility for existing listings: only explicit experience text is used.
        String text = JobContent.plainText(job.getDescriptionOfJob() == null ? "" : job.getDescriptionOfJob());
        var range = RANGE.matcher(text);
        if (range.find()) {
            int min = Integer.parseInt(range.group(1)), max = Integer.parseInt(range.group(2));
            if (min <= max && max <= 50) return new Range(min, max);
        }
        var single = SINGLE.matcher(text);
        if (single.find()) {
            int min = Integer.parseInt(single.group(1));
            if (min <= 50) return new Range(min, single.group(2) == null ? min : 50);
        }
        return null;
    }
    public static List<JobPostActivity> filter(List<JobPostActivity> jobs, String selected) {
        String filter = normalize(selected);
        if (filter.isEmpty()) return jobs;
        return jobs.stream().filter(job -> {
            Range range = range(job);
            if ("unspecified".equals(filter)) return range == null;
            if (range == null) return false;
            int min = "12+".equals(filter) ? 12 : Integer.parseInt(filter.split("-")[0]);
            int max = "12+".equals(filter) ? 50 : Integer.parseInt(filter.split("-")[1]);
            return range.min() <= max && range.max() >= min;
        }).toList();
    }
    public static String label(JobPostActivity job) {
        Range range = range(job);
        if (range == null) return "Experience not specified";
        if (range.max() == 50) return range.min() + "+ years";
        if (range.min() == range.max()) return range.min() + (range.min() == 1 ? " year" : " years");
        return range.min() + "–" + range.max() + " years";
    }
}

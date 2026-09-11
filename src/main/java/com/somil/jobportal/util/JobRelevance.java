package com.somil.jobportal.util;

import com.somil.jobportal.entity.*;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic profile matching; no profile information is sent to an AI provider. */
public final class JobRelevance {
    private static final Set<String> STOP = Set.of("a", "an", "and", "the", "with", "for", "in", "of", "at", "to", "looking", "seeking", "experienced", "professional");
    private JobRelevance() {}
    public static int score(JobSeekerProfile profile, JobPostActivity job) {
        String title = normalize(job.getJobTitle());
        String description = normalize(JobContent.plainText(Objects.toString(job.getDescriptionOfJob(), "")));
        String target = normalize(profile.getDesiredJobTitle());
        int score = !target.isBlank() && title.contains(target) ? 30 : 0;
        for (String word : tokens(target)) if (contains(title, word)) score += 8;
        for (String word : tokens(profile.getProfessionalHeadline())) if (contains(title, word)) score += 2;
        if (profile.getSkills() != null) for (Skills skill : profile.getSkills()) {
            String name = normalize(skill.getName());
            if (!name.isBlank()) {
                if (contains(title, name)) score += 12;
                else if (contains(description, name)) score += 5;
            }
        }
        // Location/workplace preferences only break ties between relevant roles.
        if (score == 0) return 0;
        if (job.getJobLocationId() != null) {
            String city = normalize(profile.getPreferredJobCity());
            if (!city.isBlank() && city.equals(normalize(job.getJobLocationId().getCity()))) score += 2;
        }
        if (!normalize(profile.getRemotePreference()).isBlank()
                && normalize(profile.getRemotePreference()).equals(normalize(job.getRemote()))) score++;
        return score;
    }
    private static Set<String> tokens(String value) {
        Set<String> words = new HashSet<>(Arrays.asList(normalize(value).split("[^\\p{L}\\p{N}+#.]+")));
        words.removeIf(word -> word.isBlank() || STOP.contains(word));
        return words;
    }
    private static boolean contains(String text, String value) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(value) + "(?![\\p{L}\\p{N}])").matcher(text).find();
    }
    private static String normalize(String value) { return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT); }
}

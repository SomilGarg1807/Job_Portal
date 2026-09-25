package com.somil.jobportal.ai.text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import com.somil.jobportal.ai.EmbeddingStrategy;
import com.somil.jobportal.entity.JobPostActivity;
import com.somil.jobportal.entity.JobSeekerProfile;
import com.somil.jobportal.entity.Skills;
import com.somil.jobportal.util.JobContent;

/**
 * Builds the exact text that is sent to Gemini for each strategy.
 *
 * <p>Left out on purpose: location, salary and company (they are SQL filters or noise), and
 * for candidates their name, contact details, pay, work authorization and resume. Personal
 * details must never influence a match or leave the site without consent.
 *
 * <p>An empty result means "nothing worth embedding"; the caller skips it.
 */
public final class EmbeddingTexts {
    /** Headings that introduce the part of a job description saying what the candidate needs. */
    private static final Pattern REQUIREMENT_HEADING = Pattern.compile(
            "(?i)\\b(requirements?|qualifications?|skills?|must[- ]haves?|nice[- ]to[- ]haves?|what you('ll)? (need|bring)"
                    + "|you (have|bring|are)|who you are|experience|tech(nology)? stack|preferred)\\b");

    private EmbeddingTexts() { }

    public static String job(EmbeddingStrategy strategy, JobPostActivity job, int maxChars) {
        return switch (strategy) {
            case JOB_FULL_V1 -> truncate(lines(
                    field("Job title", job.getJobTitle()),
                    field("Employment type", job.getJobType()),
                    field("Work mode", job.getRemote()),
                    field("Experience", job.getMinExperienceYears() == null && job.getMaxExperienceYears() == null
                            ? null : job.getExperienceLabel()),
                    field("Description", JobContent.plainText(job.getDescriptionOfJob()))), maxChars);
            case JOB_REQUIREMENTS_V1 -> truncate(lines(
                    field("Job title", job.getJobTitle()),
                    field("Experience", job.getMinExperienceYears() == null && job.getMaxExperienceYears() == null
                            ? null : job.getExperienceLabel()),
                    field("Requirements", requirements(job.getDescriptionOfJob()))), maxChars);
            default -> throw new IllegalArgumentException(strategy.id() + " is not a job strategy");
        };
    }

    public static String candidate(EmbeddingStrategy strategy, JobSeekerProfile profile, int maxChars) {
        if (strategy != EmbeddingStrategy.CANDIDATE_PROFILE_V1) {
            throw new IllegalArgumentException(strategy.id() + " is not a candidate strategy");
        }
        String skills = skills(profile.getSkills());
        // Experience or a job-type preference alone says nothing about what the person does.
        if (isBlank(profile.getProfessionalHeadline()) && isBlank(profile.getDesiredJobTitle()) && skills.isEmpty()) {
            return "";
        }
        return truncate(lines(
                field("Headline", profile.getProfessionalHeadline()),
                field("Desired role", profile.getDesiredJobTitle()),
                field("Total experience", years(profile.getTotalExperienceYears())),
                field("Employment type", profile.getEmploymentType()),
                field("Work mode", profile.getRemotePreference()),
                field("Skills", skills)), maxChars);
    }

    /**
     * Text under requirement-like headings. A heading is a real h1-h6, or a short paragraph
     * that is bold or ends with a colon (how most people format headings in the editor).
     * Falls back to every bullet point, then to the whole description, so the result is
     * never empty when the description is not.
     */
    static String requirements(String html) {
        var body = Jsoup.parse(Objects.toString(html, "")).body();
        List<String> picked = new ArrayList<>();
        boolean inSection = false;
        for (Element element : body.children()) {
            String text = element.text().trim();
            if (text.isEmpty()) continue;
            if (isHeading(element, text)) {
                inSection = REQUIREMENT_HEADING.matcher(text).find();
                continue;
            }
            if (inSection) picked.add(text);
        }
        if (picked.isEmpty()) {
            body.select("li").forEach(item -> { if (!item.text().isBlank()) picked.add(item.text().trim()); });
        }
        return picked.isEmpty() ? JobContent.plainText(html) : String.join("\n", picked);
    }

    private static boolean isHeading(Element element, String text) {
        if (element.tagName().matches("h[1-6]")) return true;
        if (text.length() > 80) return false;
        boolean bold = element.children().size() == 1
                && element.child(0).tagName().matches("b|strong")
                && element.child(0).text().trim().equals(text);
        return element.tagName().equals("p") && (bold || text.endsWith(":"));
    }

    private static String skills(List<Skills> skills) {
        if (skills == null) return "";
        List<String> parts = new ArrayList<>();
        for (Skills skill : skills) {
            if (isBlank(skill.getName())) continue;
            List<String> detail = new ArrayList<>();
            if (!isBlank(skill.getExperienceLevel())) detail.add(skill.getExperienceLevel().trim());
            if (!isBlank(skill.getYearsOfExperience())) detail.add(skill.getYearsOfExperience().trim() + " years");
            parts.add(skill.getName().trim() + (detail.isEmpty() ? "" : " (" + String.join(", ", detail) + ")"));
        }
        return String.join("; ", parts);
    }

    private static String years(BigDecimal years) {
        return years == null ? null : years.stripTrailingZeros().toPlainString() + " years";
    }

    private static String field(String label, String value) {
        return isBlank(value) ? null : label + ": " + value.trim();
    }

    private static String lines(String... fields) {
        List<String> present = new ArrayList<>();
        for (String field : fields) if (field != null) present.add(field);
        return String.join("\n", present);
    }

    /** Cuts at a word boundary so the model never sees half a word. */
    static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        int cut = text.lastIndexOf(' ', maxChars);
        return text.substring(0, cut > maxChars / 2 ? cut : maxChars);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

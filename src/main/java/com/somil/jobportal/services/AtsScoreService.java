package com.somil.jobportal.services;

import com.somil.jobportal.entity.*;
import com.somil.jobportal.util.JobContent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic, explainable ATS-style score: keyword coverage of the job description by the resume.
 * Runs locally; no resume text is sent to an AI provider.
 */
@Service
public class AtsScoreService {
    /** Known multi-word and single-word skills; matched as whole terms in the job description. */
    private static final List<String> VOCABULARY = List.of(
            "java", "spring boot", "spring", "hibernate", "jpa", "microservices", "rest", "rest api", "graphql", "kotlin",
            "python", "django", "flask", "fastapi", "javascript", "typescript", "react", "angular", "vue", "node.js", "node",
            "express", "next.js", "html", "css", "tailwind", "sass", "redux", "c#", ".net", "asp.net", "c++", "go", "golang",
            "rust", "php", "laravel", "ruby", "rails", "scala", "swift", "android", "ios", "flutter", "react native",
            "sql", "mysql", "postgresql", "oracle", "mongodb", "redis", "cassandra", "elasticsearch", "kafka", "rabbitmq",
            "aws", "azure", "gcp", "google cloud", "docker", "kubernetes", "terraform", "ansible", "jenkins", "ci/cd",
            "github actions", "git", "linux", "bash", "maven", "gradle", "junit", "mockito", "selenium", "cypress", "jest",
            "unit testing", "testing", "tdd", "agile", "scrum", "jira", "devops", "machine learning", "deep learning",
            "nlp", "tensorflow", "pytorch", "pandas", "numpy", "spark", "hadoop", "airflow", "data analysis", "power bi",
            "tableau", "excel", "figma", "ui/ux", "system design", "data structures", "algorithms", "oop", "design patterns",
            "security", "oauth", "jwt", "api", "apis", "cloud", "serverless", "lambda", "monitoring", "performance");
    private static final Set<String> STOP = Set.of(
            "about", "above", "after", "again", "also", "and", "any", "are", "able", "bring", "build", "candidate", "company",
            "could", "every", "experience", "from", "good", "great", "have", "help", "ideal", "including", "into", "join",
            "knowledge", "looking", "must", "nice", "other", "our", "plus", "preferred", "required", "requirements",
            "responsibilities", "role", "should", "skills", "strong", "such", "team", "that", "their", "them", "then",
            "there", "these", "they", "this", "those", "through", "using", "very", "want", "well", "were", "what", "when",
            "where", "which", "while", "will", "with", "within", "work", "working", "would", "year", "years", "your", "you");

    private final CandidateAccessService files;
    private final ResumeTextService resumes;

    public AtsScoreService(CandidateAccessService files, ResumeTextService resumes) {
        this.files = files; this.resumes = resumes;
    }

    /** Scores the candidate's stored resume PDF (if readable) and profile against the job. */
    public AtsReport score(JobSeekerProfile profile, JobPostActivity job) {
        String text = "";
        String resume = profile.getResume();
        if (resume != null && resume.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            try { text = resumes.extract(files.file(profile, resume)); }
            catch (java.io.IOException | RuntimeException ignored) { text = ""; }
        }
        return score(profile, job, text);
    }

    public record AtsReport(int score, String band, int keywordScore, int roleScore, int experienceScore,
                            int resumeScore, List<String> matched, List<String> missing, boolean resumeRead, String note) {}

    public AtsReport score(JobSeekerProfile profile, JobPostActivity job, String resumeText) {
        String resume = normalize(resumeText);
        StringBuilder evidence = new StringBuilder(resume);
        if (profile.getSkills() != null) for (Skills s : profile.getSkills()) evidence.append(' ').append(normalize(s.getName()));
        evidence.append(' ').append(normalize(profile.getProfessionalHeadline())).append(' ').append(normalize(profile.getDesiredJobTitle()));
        String candidate = evidence.toString();

        String title = normalize(job.getJobTitle());
        String description = normalize(JobContent.plainText(Objects.toString(job.getDescriptionOfJob(), "")));
        List<String> keywords = keywords(title + " " + description);

        List<String> matched = new ArrayList<>(), missing = new ArrayList<>();
        for (String keyword : keywords) (contains(candidate, keyword) ? matched : missing).add(keyword);
        int keywordScore = keywords.isEmpty() ? 50 : Math.round(100f * matched.size() / keywords.size());

        List<String> titleWords = tokens(title);
        long titleHits = titleWords.stream().filter(word -> contains(candidate, word)).count();
        int roleScore = titleWords.isEmpty() ? 100 : (int) Math.round(100.0 * titleHits / titleWords.size());

        int experienceScore = experience(profile.getTotalExperienceYears(), job.getMinExperienceYears(), job.getMaxExperienceYears());

        boolean resumeRead = !resume.isBlank();
        int resumeScore = 0;
        if (resumeRead) {
            resumeScore += 40;
            for (String section : List.of("experience", "education", "skills", "project"))
                if (resume.contains(section)) resumeScore += 15;
        }

        int score = Math.round(keywordScore * 0.6f + roleScore * 0.15f + experienceScore * 0.15f + resumeScore * 0.10f);
        score = Math.max(0, Math.min(100, score));
        String band = score >= 75 ? "Strong match" : score >= 55 ? "Good match" : score >= 35 ? "Partial match" : "Low match";
        String note = resumeRead ? "Scored from the resume PDF and profile skills against the job description."
                : "No readable resume PDF was found, so only profile skills and headline were scored.";
        return new AtsReport(score, band, keywordScore, roleScore, experienceScore, resumeScore,
                matched.stream().limit(20).toList(), missing.stream().limit(20).toList(), resumeRead, note);
    }

    List<String> keywords(String jd) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (String term : VOCABULARY) if (contains(jd, term)) found.add(term);
        // Drop terms fully covered by a longer match ("spring" when "spring boot" is present).
        found.removeIf(term -> found.stream().anyMatch(other -> !other.equals(term) && other.contains(term) && other.length() > term.length()));
        if (found.size() < 5) {
            Map<String, Integer> counts = new HashMap<>();
            for (String word : jd.split("[^\\p{L}\\p{N}+#.]+")) {
                word = word.replaceAll("^\\.+|\\.+$", "");
                if (word.length() >= 4 && !STOP.contains(word) && !word.chars().allMatch(Character::isDigit))
                    counts.merge(word, 1, Integer::sum);
            }
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey).filter(word -> found.stream().noneMatch(term -> term.contains(word)))
                    .limit(10 - found.size()).forEach(found::add);
        }
        return new ArrayList<>(found);
    }

    private static int experience(BigDecimal years, Integer min, Integer max) {
        if (min == null && max == null) return 100;
        if (years == null) return 50;
        double y = years.doubleValue();
        if (min != null && y < min) return (int) Math.max(0, Math.round(100 - (min - y) * 30));
        if (max != null && y > max + 2) return 80;
        return 100;
    }

    private static List<String> tokens(String value) {
        return Arrays.stream(value.split("[^\\p{L}\\p{N}+#.]+"))
                .filter(w -> w.length() > 1 && !STOP.contains(w) && !Set.of("senior", "junior", "lead", "sr", "jr", "i", "ii", "iii").contains(w))
                .distinct().toList();
    }

    private static boolean contains(String text, String term) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])").matcher(text).find();
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}

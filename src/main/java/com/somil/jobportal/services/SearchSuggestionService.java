package com.somil.jobportal.services;

import com.somil.jobportal.repository.JobPostActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;

@Service
public class SearchSuggestionService {
    public record Suggestion(String value, String label, String kind) {}
    public record Place(String name, String admin1, String country, String feature_code) {}
    public record Places(List<Place> results) {}
    private record Cached(Instant expires, List<Place> places) {}
    private final JobPostActivityRepository jobs;
    private final RestClient geography;
    private final Map<String, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>());

    @org.springframework.beans.factory.annotation.Autowired
    public SearchSuggestionService(JobPostActivityRepository jobs) {
        this(jobs, geographyClient());
    }
    SearchSuggestionService(JobPostActivityRepository jobs, RestClient geography) {
        this.jobs = jobs;
        this.geography = geography;
    }
    private static RestClient geographyClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        return RestClient.builder().requestFactory(factory)
                .baseUrl("https://geocoding-api.open-meteo.com").build();
    }

    public List<Suggestion> jobs(String query) {
        String q = normalize(query);
        if (q.isEmpty()) return List.of();
        List<Suggestion> result = new ArrayList<>();
        jobs.suggestTitles(q, PageRequest.of(0, 7)).forEach(title ->
                result.add(new Suggestion(title, title, "Job title")));
        jobs.suggestCompanies(q, PageRequest.of(0, 2)).forEach(company ->
                result.add(new Suggestion(company, company, "Company")));
        if (!result.isEmpty()) result.add(0, new Suggestion(q, "Search all jobs for “" + q + "”", "Keyword"));
        return result;
    }

    public List<Suggestion> locations(String query) {
        String q = normalize(query);
        if (q.isEmpty()) return List.of();
        Map<String, Suggestion> result = new LinkedHashMap<>();
        jobs.suggestLocations(q, PageRequest.of(0, 12)).forEach(location -> {
            add(result, q, location.getCity(), join(location.getCity(), location.getState(), location.getCountry()), "City · posted jobs");
            add(result, q, location.getState(), join(location.getState(), location.getCountry()), "State · posted jobs");
            add(result, q, location.getCountry(), location.getCountry(), "Country · posted jobs");
        });
        for (Place place : places(q)) {
            String kind = "PCLI".equals(place.feature_code()) ? "Country" : "ADM1".equals(place.feature_code()) ? "State" : "City";
            add(result, q, place.name(), join(place.name(), place.admin1(), place.country()), kind);
            add(result, q, place.admin1(), join(place.admin1(), place.country()), "State");
            add(result, q, place.country(), place.country(), "Country");
        }
        return result.values().stream().limit(10).toList();
    }

    private List<Place> places(String q) {
        String key = q.toLowerCase(Locale.ROOT);
        Cached found = cache.get(key);
        if (found != null && found.expires().isAfter(Instant.now())) return found.places();
        List<Place> places;
        try {
            Places response = geography.get().uri(builder -> builder.path("/v1/search")
                    .queryParam("name", q).queryParam("count", 8).queryParam("language", "en").build())
                    .retrieve().body(Places.class);
            places = response == null || response.results() == null ? List.of() : response.results();
        } catch (org.springframework.web.client.RestClientException unavailable) {
            return List.of();
        }
        synchronized (cache) {
            if (cache.size() >= 200) cache.remove(cache.keySet().iterator().next());
            cache.put(key, new Cached(Instant.now().plusSeconds(1800), places));
        }
        return places;
    }

    private static void add(Map<String, Suggestion> result, String q, String value, String label, String kind) {
        if (value != null && value.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))
            result.putIfAbsent(value.toLowerCase(Locale.ROOT), new Suggestion(value, label, kind));
    }
    private static String join(String... parts) {
        return String.join(", ", Arrays.stream(parts).filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList());
    }
    private static String normalize(String q) {
        return q == null || q.trim().length() < 2 || q.trim().length() > 100 ? "" : q.trim();
    }
}

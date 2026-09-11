package com.somil.jobportal.services;

import com.somil.jobportal.entity.JobLocation;
import com.somil.jobportal.repository.JobPostActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SearchSuggestionServiceTests {
    JobPostActivityRepository jobs = mock(JobPostActivityRepository.class);
    RestClient.Builder builder = RestClient.builder().baseUrl("https://geocoding-api.open-meteo.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    SearchSuggestionService service = new SearchSuggestionService(jobs, builder.build());

    @Test void combinesPostedLocationsWithCachedGeography() {
        when(jobs.suggestLocations(eq("Pun"), any())).thenReturn(List.of(new JobLocation(1, "Pune", "Maharashtra", "India")));
        server.expect(requestTo("https://geocoding-api.open-meteo.com/v1/search?name=Pun&count=8&language=en"))
                .andRespond(withSuccess("{\"results\":[{\"name\":\"Pune\",\"admin1\":\"Maharashtra\",\"country\":\"India\",\"feature_code\":\"PPLA2\",\"latitude\":18.5},{\"name\":\"Punta Arenas\",\"country\":\"Chile\"}]}", MediaType.APPLICATION_JSON));
        var result = service.locations("Pun");
        assertThat(result).extracting(SearchSuggestionService.Suggestion::value).containsExactly("Pune", "Punta Arenas");
        assertThat(service.locations("Pun")).isEqualTo(result);
        server.verify();
    }
    @Test void geographyFailureStillReturnsPostedLocations() {
        when(jobs.suggestLocations(eq("India"), any())).thenReturn(List.of(new JobLocation(1, "Pune", "Maharashtra", "India")));
        server.expect(anything()).andRespond(withServerError());
        assertThat(service.locations("India")).extracting(SearchSuggestionService.Suggestion::value).containsExactly("India");
        server.verify();
    }
    @Test void suggestsActualTitlesAndKeywordsAndRejectsOversizedQueries() {
        when(jobs.suggestTitles(eq("Java"), any())).thenReturn(List.of("Java Engineer"));
        when(jobs.suggestCompanies(eq("Java"), any())).thenReturn(List.of("Java Labs"));
        assertThat(service.jobs(" Java ")).extracting(SearchSuggestionService.Suggestion::value).containsExactly("Java", "Java Engineer", "Java Labs");
        assertThat(service.jobs("x".repeat(101))).isEmpty();
        assertThat(service.locations("x")).isEmpty();
        server.verify();
    }
}

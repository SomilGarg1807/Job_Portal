package com.somil.jobportal.controller;

import com.somil.jobportal.services.SearchSuggestionService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchSuggestionController {
    private final SearchSuggestionService suggestions;
    public SearchSuggestionController(SearchSuggestionService suggestions) { this.suggestions = suggestions; }
    @GetMapping("/jobs")
    public List<SearchSuggestionService.Suggestion> jobs(@RequestParam(defaultValue = "") String q) {
        return suggestions.jobs(q);
    }
    @GetMapping("/locations")
    public List<SearchSuggestionService.Suggestion> locations(@RequestParam(defaultValue = "") String q) {
        return suggestions.locations(q);
    }
}

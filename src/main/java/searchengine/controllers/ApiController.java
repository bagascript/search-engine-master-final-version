package searchengine.controllers;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import searchengine.dto.response.ApiResponse;

import searchengine.services.indexation.IndexationService;
import searchengine.services.search.SearchService;
import searchengine.services.statistics.StatisticsService;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController
{
    @Autowired
    private final IndexationService indexationService;

    @Autowired
    private final StatisticsService statisticsService;

    @Autowired
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        return ResponseEntity.ok(indexationService.startIndexingApiResponse());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        return ResponseEntity.ok(indexationService.stopIndexingApiResponse());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<ApiResponse> indexPage(@RequestParam String url) {
        return ResponseEntity.ok(indexationService.indexPageApiResponse(url));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse> search(
            @RequestParam(name = "query", required = false, defaultValue = "") String query,
            @RequestParam(name = "site", required = false, defaultValue = "") String site,
            int offset, int limit) throws IOException {

        if(site.isEmpty()) {
            return ResponseEntity.ok(searchService.searchForAllSites(query));
        } else {
            return ResponseEntity.ok(searchService.searchForOneSite(query, site));
        }
    }
}

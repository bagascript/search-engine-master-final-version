package searchengine.controllers;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import searchengine.dto.response.ApiResponses;

import searchengine.services.indexation.IndexationService;
import searchengine.services.search.SearchService;
import searchengine.services.statistics.StatisticsService;

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
    public ResponseEntity<ApiResponses> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponses> startIndexing() {
        return ResponseEntity.ok(indexationService.startIndexingApiResponse());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponses> stopIndexing() {
        return ResponseEntity.ok(indexationService.stopIndexingApiResponse());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<ApiResponses> indexPage(@RequestParam String url) {
        return ResponseEntity.ok(indexationService.indexPageApiResponse(url));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponses> search(
            @RequestParam(name = "query", required = false, defaultValue = "") String query,
            @RequestParam(name = "site", required = false, defaultValue = "") String site,
            int offset, int limit) {
        if(site.isEmpty()) {
            return ResponseEntity.ok(searchService.searchForAllSites(query));
        } else {
            return ResponseEntity.ok(searchService.searchForOneSite(query, site));
        }
    }
}

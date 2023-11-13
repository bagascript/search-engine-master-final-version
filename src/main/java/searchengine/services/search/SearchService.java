package searchengine.services.search;

import searchengine.dto.response.ApiResponse;

import java.io.IOException;

public interface SearchService
{
    ApiResponse searchForOneSite(String query, String site);

    ApiResponse searchForAllSites(String query);
}

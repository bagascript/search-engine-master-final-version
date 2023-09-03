package searchengine.services.search;

import searchengine.dto.response.ApiResponse;

public interface SearchService
{
    ApiResponse searchForOneSite(String query, String site);

    ApiResponse searchForAllSites(String query);
}

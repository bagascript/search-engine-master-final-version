package searchengine.services.search;

import searchengine.dto.response.ApiResponses;

public interface SearchService
{
    ApiResponses searchForOneSite(String query, String site);

    ApiResponses searchForAllSites(String query);
}

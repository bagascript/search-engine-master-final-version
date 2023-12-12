package searchengine.services.indexation;

import searchengine.dto.response.ApiResponses;

public interface IndexationService
{
    ApiResponses startIndexingApiResponse();

    ApiResponses stopIndexingApiResponse();

    ApiResponses indexPageApiResponse(String url);
}

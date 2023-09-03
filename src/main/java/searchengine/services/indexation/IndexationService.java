package searchengine.services.indexation;

import searchengine.dto.response.ApiResponse;

public interface IndexationService
{
    ApiResponse startIndexingApiResponse();

    ApiResponse stopIndexingApiResponse();

    ApiResponse indexPageApiResponse(String url);
}

package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponse;
import searchengine.dto.search.*;
import searchengine.lemma.LemmaConverter;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.proccessing.PageFinder;
import searchengine.proccessing.RelevanceCalculation;
import searchengine.proccessing.SnippetCreation;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {
    private static final String EMPTY_QUERY_ERROR_TEXT = "Задан пустой поисковый запрос";
    private static final String SITE_IS_NOT_INDEXED_ERROR_TEXT = "Сайт/сайты ещё не проиндексированы";

    private static boolean isItSearchOfAllSites = false;

    private final LemmaConverter lemmaConverter;
    private final SnippetCreation snippetCreation;
    private final RelevanceCalculation relevanceCalculation;
    private final PageFinder pageFinder;
    private final SitesList sites;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final IndexRepository indexRepository;

    @Override
    public ApiResponse searchForOneSite(String query, String site) {
        ApiResponse apiResponse;
        SiteEntity siteEntity = siteRepository.findByUrl(site);
        if (!siteEntity.getStatus().name().equals("INDEXED")) {
            apiResponse = new ApiResponse(false, SITE_IS_NOT_INDEXED_ERROR_TEXT);
        } else if (query.isEmpty()) {
            apiResponse = new ApiResponse(false, EMPTY_QUERY_ERROR_TEXT);
        } else {
            apiResponse = getFoundResultsResponse(query, siteEntity);
        }
        return apiResponse;
    }

    @Override
    public ApiResponse searchForAllSites(String query) {
        List<ApiResponse> apiResponseList = new ArrayList<>();
        List<DataProperties> totalDataProperties = new ArrayList<>();
        List<String> totalError = new ArrayList<>();
        ApiResponse totalApiResponse;
        int totalCount = 0;
        isItSearchOfAllSites = true;

        for (Site site : sites.getSites()) {
            ApiResponse apiResponse = searchForOneSite(query, site.getUrl());
            apiResponseList.add(apiResponse);
            totalError.add(apiResponse.getError());
        }

        if (apiResponseList.stream().anyMatch(s -> !s.isResult())) {
            totalApiResponse = sendNegativeResponse(totalError);
        } else {
            for (ApiResponse apiResponse : apiResponseList) {
                if (apiResponse.getData().isEmpty()) continue;
                totalCount += apiResponse.getCount();
                totalDataProperties.addAll(apiResponse.getData());
            }
            totalApiResponse = sendPositiveResponse(totalCount, totalDataProperties);
        }
        isItSearchOfAllSites = false;
        return totalApiResponse;
    }

    private ApiResponse getFoundResultsResponse(String query, SiteEntity siteEntity) {
        ApiResponse apiResponse;
        List<DataProperties> dataPropertiesList;
        String[] words = lemmaConverter.splitContentIntoWords(query);
        Set<LemmaEntity> resultWordsSet = pageFinder.editQueryWords(words, siteEntity);
        if (resultWordsSet.isEmpty()) {
            dataPropertiesList = new ArrayList<>();
            apiResponse = new ApiResponse(true, 0, dataPropertiesList);
        } else {
            Set<LemmaEntity> lemmas = pageFinder.getLemmasSet(resultWordsSet);
            String firstLemma = lemmas.stream().findFirst().orElseThrow().getLemma();
            int lemmaId = lemmaRepository.getLemmaEntity(firstLemma, siteEntity).getId();
            List<IndexEntity> indexes = indexRepository.findAllByLemmaId(lemmaId);
            Set<PageEntity> pageEntities = pageFinder.getPages(indexes, lemmas.stream().skip(1).collect(Collectors.toSet()));

            if (pageEntities.isEmpty()) {
                dataPropertiesList = new ArrayList<>();
                apiResponse = new ApiResponse(true, 0, dataPropertiesList);
            } else {
                dataPropertiesList = saveDataIntoList(pageEntities, lemmas);
                apiResponse = getDataPropertiesListInfo(dataPropertiesList);
            }
        }
        return apiResponse;
    }

    private List<DataProperties> saveDataIntoList(Set<PageEntity> pageEntities, Set<LemmaEntity> lemmas) {
        List<DataProperties> dataPropertiesList = new ArrayList<>();
        float maxAbsoluteRelevance = relevanceCalculation.getMaxPageRelevance(pageEntities, lemmas);
        for (PageEntity pageEntity : pageEntities) {
            DataProperties dataProperties = new DataProperties();
            Link link = editPageProperties(pageEntity.getSite().getUrl(), pageEntity.getPath(), pageEntity.getContent());
            dataProperties.setSiteName(pageEntity.getSite().getName());
            dataProperties.setSite(link.getSite());
            dataProperties.setUri(link.getUri());
            dataProperties.setTitle(link.getTitle());
            dataProperties.setRelevance(relevanceCalculation.calculatePageRelevance(pageEntity, maxAbsoluteRelevance, lemmas));
            dataProperties.setSnippet(snippetCreation.getSnippetFromPageContent(link.getContent(), lemmas));
            if (dataProperties.getSnippet().length() <= 3) continue;
            dataPropertiesList.add(dataProperties);
        }

        return dataPropertiesList;
    }

    private Link editPageProperties(String siteURL, String uri, String pageContent) {
        Link link = new Link();
        String finalSiteURLVersion = lemmaConverter.editSiteURL(siteURL);
        String title = pageContent.substring(pageContent.indexOf("<title>") + "<title>".length(), pageContent.indexOf("</title>"));
        String content = Jsoup.parse(pageContent).body().text();
        link.setSite(finalSiteURLVersion);
        link.setUri(uri);
        link.setTitle(title);
        link.setContent(content);
        return link;
    }

    private ApiResponse getDataPropertiesListInfo(List<DataProperties> dataPropertiesList) {
        ApiResponse apiResponse;
        if(!isItSearchOfAllSites) {
            List<DataProperties> reducedDataPropertiesList = dataPropertiesList.subList(0, Math.min(dataPropertiesList.size(), 20));
            apiResponse = new ApiResponse(true, dataPropertiesList.size(),
                    reducedDataPropertiesList.stream()
                            .sorted(Collections.reverseOrder(Comparator.comparing(DataProperties::getRelevance)))
                            .collect(Collectors.toList()));
        } else {
            apiResponse = new ApiResponse(true, dataPropertiesList.size(),
                    dataPropertiesList.stream()
                            .sorted(Collections.reverseOrder(Comparator.comparing(DataProperties::getRelevance)))
                            .collect(Collectors.toList()));
        }

        return apiResponse;
    }

    private ApiResponse sendNegativeResponse(List<String> totalError) {
        ApiResponse totalApiResponse;
        if (totalError.contains(SITE_IS_NOT_INDEXED_ERROR_TEXT)) {
            totalApiResponse = new ApiResponse(false, SITE_IS_NOT_INDEXED_ERROR_TEXT);
        } else {
            totalApiResponse = new ApiResponse(false, EMPTY_QUERY_ERROR_TEXT);
        }
        return totalApiResponse;
    }

    private ApiResponse sendPositiveResponse(int totalCount, List<DataProperties> totalDataProperties) {
        ApiResponse totalApiResponse;
        if (totalCount == 0 && totalDataProperties.isEmpty()) {
            totalApiResponse = new ApiResponse(true, 0, totalDataProperties);
        } else {
            List<DataProperties> total = totalDataProperties.subList(0, Math.min(totalDataProperties.size(), 20));
            totalApiResponse = new ApiResponse(true, totalDataProperties.size(),
                    total.stream()
                            .sorted(Collections.reverseOrder(Comparator.comparing(DataProperties::getRelevance)))
                            .collect(Collectors.toList()));
        }
        return totalApiResponse;
    }
}

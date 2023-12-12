package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponses;
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
import searchengine.dto.response.ServerResponses;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {
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
    public ApiResponses searchForOneSite(String query, String site) {
        ApiResponses apiResponses;
        SiteEntity siteEntity = siteRepository.findByUrl(site);
        if (!siteEntity.getStatus().name().equals("INDEXED")) {
            apiResponses = new ApiResponses(false, ServerResponses.SITE_IS_NOT_INDEXED_ERROR_TEXT);
        } else if (query.isEmpty()) {
            apiResponses = new ApiResponses(false, ServerResponses.EMPTY_QUERY_ERROR_TEXT);
        } else {
            apiResponses = getFoundResultsResponse(query, siteEntity);
        }
        return apiResponses;
    }

    @Override
    public ApiResponses searchForAllSites(String query) {
        List<ApiResponses> apiResponsesList = new ArrayList<>();
        List<DataProperties> totalDataProperties = new ArrayList<>();
        List<String> totalError = new ArrayList<>();
        ApiResponses totalApiResponses;
        int totalCount = 0;
        isItSearchOfAllSites = true;

        for (Site site : sites.getSites()) {
            ApiResponses apiResponses = searchForOneSite(query, site.getUrl());
            apiResponsesList.add(apiResponses);
            totalError.add(apiResponses.getError());
        }

        if (apiResponsesList.stream().anyMatch(s -> !s.isResult())) {
            totalApiResponses = sendNegativeResponse(totalError);
        } else {
            for (ApiResponses apiResponses : apiResponsesList) {
                if (apiResponses.getData().isEmpty()) {
                    continue;
                }
                totalCount += apiResponses.getCount();
                totalDataProperties.addAll(apiResponses.getData());
            }
            totalApiResponses = sendPositiveResponse(totalCount, totalDataProperties);
        }
        isItSearchOfAllSites = false;
        return totalApiResponses;
    }

    private ApiResponses getFoundResultsResponse(String query, SiteEntity siteEntity) {
        ApiResponses apiResponses;
        List<DataProperties> dataPropertiesList;
        String[] words = lemmaConverter.splitContentIntoWords(query);
        Set<LemmaEntity> resultWordsSet = pageFinder.editQueryWords(words, siteEntity);
        if (resultWordsSet.isEmpty()) {
            dataPropertiesList = new ArrayList<>();
            apiResponses = new ApiResponses(true, 0, dataPropertiesList);
        } else {
            Set<LemmaEntity> lemmas = pageFinder.getLemmasSet(resultWordsSet);
            String firstLemma = lemmas.stream().findFirst().orElseThrow().getLemma();
            int lemmaId = lemmaRepository.getLemmaEntity(firstLemma, siteEntity).getId();
            List<IndexEntity> indexes = indexRepository.findAllByLemmaId(lemmaId);
            Set<PageEntity> pageEntities = pageFinder.getPages(indexes, lemmas.stream().skip(1).collect(Collectors.toSet()));

            if (pageEntities.isEmpty()) {
                dataPropertiesList = new ArrayList<>();
                apiResponses = new ApiResponses(true, 0, dataPropertiesList);
            } else {
                dataPropertiesList = saveDataIntoList(pageEntities, lemmas);
                apiResponses = getDataPropertiesListInfo(dataPropertiesList);
            }
        }
        return apiResponses;
    }

    private List<DataProperties> saveDataIntoList(Set<PageEntity> pageEntities, Set<LemmaEntity> lemmas) {
        List<DataProperties> dataPropertiesList = new ArrayList<>();
        List<PageRelevance> pageRelevanceList = relevanceCalculation.searchForAllLemmaIndexes(lemmas, pageEntities);
        float maxRelevance = pageRelevanceList.get(0).getMaxRank();
        for (PageRelevance pageRelevance : pageRelevanceList) {
            DataProperties dataProperties = new DataProperties();
            PageEntity page = pageRelevance.getPageEntity();
            Link link = editPageProperties(page.getSite().getUrl(), page.getPath(), page.getContent());
            dataProperties.setSiteName(page.getSite().getName());
            dataProperties.setSite(link.getSite());
            dataProperties.setUri(link.getUri());
            dataProperties.setTitle(link.getTitle());
            dataProperties.setRelevance(pageRelevance.getAbsoluteRank() / maxRelevance);
            dataProperties.setSnippet(snippetCreation.getSnippetFromPageContent(link.getContent(), lemmas));
            if (dataProperties.getSnippet().length() <= 3) {
                continue;
            }
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

    private ApiResponses getDataPropertiesListInfo(List<DataProperties> dataPropertiesList) {
        ApiResponses apiResponses;
        if(!isItSearchOfAllSites) {
            dataPropertiesList = dataPropertiesList.subList(0, Math.min(dataPropertiesList.size(), 20));
        }
        apiResponses = new ApiResponses(true, dataPropertiesList.size(),
                dataPropertiesList.stream()
                        .sorted(Collections.reverseOrder(Comparator.comparing(DataProperties::getRelevance)))
                        .collect(Collectors.toList()));
        return apiResponses;
    }

    private ApiResponses sendNegativeResponse(List<String> totalError) {
        ApiResponses totalApiResponses;
        if (totalError.contains(ServerResponses.SITE_IS_NOT_INDEXED_ERROR_TEXT)) {
            totalApiResponses = new ApiResponses(false, ServerResponses.SITE_IS_NOT_INDEXED_ERROR_TEXT);
        } else {
            totalApiResponses = new ApiResponses(false, ServerResponses.EMPTY_QUERY_ERROR_TEXT);
        }
        return totalApiResponses;
    }

    private ApiResponses sendPositiveResponse(int totalCount, List<DataProperties> totalDataProperties) {
        ApiResponses totalApiResponses;
        if (totalCount == 0 && totalDataProperties.isEmpty()) {
            totalApiResponses = new ApiResponses(true, 0, totalDataProperties);
        } else {
            List<DataProperties> total = totalDataProperties.subList(0, Math.min(totalDataProperties.size(), 20));
            totalApiResponses = new ApiResponses(true, totalDataProperties.size(),
                    total.stream()
                            .sorted(Collections.reverseOrder(Comparator.comparing(DataProperties::getRelevance)))
                            .collect(Collectors.toList()));
        }
        return totalApiResponses;
    }
}

package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponse;
import searchengine.dto.search.DataProperties;
import searchengine.dto.search.Link;
import searchengine.lemma.Lemma;
import searchengine.lemma.LemmaConverter;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {
    private static final String EMPTY_QUERY_ERROR_TEXT = "Задан пустой поисковый запрос";
    private static final String SITE_IS_NOT_INDEXED_ERROR_TEXT = "Сайт/сайты ещё не проиндексированы";

    private final LemmaConverter lemmaConverter;

    private final SitesList sites;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final PageRepository pageRepository;

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
            apiResponse = getFoundResultsResponse(query, site, siteEntity);
        }
        return apiResponse;
    }

    private ApiResponse getFoundResultsResponse(String query, String site, SiteEntity siteEntity) {
        ApiResponse apiResponse;
        List<DataProperties> dataPropertiesList;
        String[] words = lemmaConverter.getSplitContentIntoWords(query);
        Set<String> result = editQueryWords(words, siteEntity);
        if (result.isEmpty()) {
            dataPropertiesList = new ArrayList<>();
            apiResponse = new ApiResponse(true, 0, dataPropertiesList);
        } else {
            Set<Lemma> lemmas = getLemmasSet(result, site);
            String firstLemma = lemmas.stream().findFirst().get().getLemma();
            int lemmaId = lemmaRepository.getLemmaEntity(firstLemma, siteEntity).getId();
            List<IndexEntity> indexes = indexRepository.findAllByLemmaId(lemmaId);
            Set<PageEntity> pageEntities = getPages(indexes, lemmas, siteEntity);
            if (pageEntities.isEmpty()) {
                dataPropertiesList = new ArrayList<>();
                apiResponse = new ApiResponse(true, 0, dataPropertiesList);
            } else {
                dataPropertiesList = saveDataIntoList(pageEntities, siteEntity, lemmas, query);
                apiResponse = new ApiResponse(true, dataPropertiesList.size(),
                        dataPropertiesList.stream()
                                .sorted(Collections.reverseOrder(Comparator.comparing(DataProperties::getRelevance)))
                                .collect(Collectors.toList()));
            }
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

        for (Site site : sites.getSites()) {
            ApiResponse apiResponse = searchForOneSite(query, site.getUrl());
            apiResponseList.add(apiResponse);
            totalError.add(apiResponse.getError());
        }

        if(apiResponseList.stream().anyMatch(s -> !s.isResult())) {
            totalApiResponse = sendNegativeResponse(totalError);
        } else {
            for (ApiResponse apiResponse : apiResponseList) {
                if(apiResponse.getData().isEmpty()) {
                    continue;
                }
                totalCount += apiResponse.getCount();
                totalDataProperties.addAll(apiResponse.getData());
            }

            totalApiResponse = sendPositiveResponse(totalCount, totalDataProperties);
        }
        return totalApiResponse;
    }

    private ApiResponse sendNegativeResponse(List<String> totalError) {
        ApiResponse totalApiResponse;
        if (totalError.contains(SITE_IS_NOT_INDEXED_ERROR_TEXT)) {
            totalApiResponse = new ApiResponse(false, SITE_IS_NOT_INDEXED_ERROR_TEXT);
        } else  {
            totalApiResponse = new ApiResponse(false, EMPTY_QUERY_ERROR_TEXT);
        }
        return totalApiResponse;
    }

    private ApiResponse sendPositiveResponse(int totalCount , List<DataProperties> totalDataProperties) {
        ApiResponse totalApiResponse;
        if (totalCount == 0 && totalDataProperties.isEmpty()) {
            totalApiResponse = new ApiResponse(true, 0, totalDataProperties);
        } else {
            totalApiResponse = new ApiResponse(true, totalDataProperties.size(),
                    totalDataProperties.stream()
                            .sorted(Collections.reverseOrder(Comparator.comparing(DataProperties::getRelevance)))
                            .collect(Collectors.toList()));
        }
        return totalApiResponse;
    }

    private Set<String> editQueryWords(String[] words, SiteEntity siteEntity) {
        Set<String> lemmaSet = new HashSet<>();
        for (String word : words) {
            List<String> wordBaseForms = lemmaConverter.getReturnWordIntoBaseForm(word);
            if (!wordBaseForms.isEmpty()) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                lemmaSet.add(resultWordForm);
            }
        }

        return lemmaSet.stream().filter(l -> lemmaRepository.existsByLemmaAndSiteId(l, siteEntity.getId())).collect(Collectors.toSet());
    }

    private Set<Lemma> getLemmasSet(Set<String> words, String site) {
        Set<Lemma> lemmaSet = new HashSet<>();

        SiteEntity siteEntity = siteRepository.findByUrl(site);
        List<PageEntity> pages = pageRepository.findAllBySiteId(siteEntity.getId());
        for (String word : words) {
            int lemmaFrequency = lemmaRepository.getFrequencyByLemmaAndSiteId(word, siteEntity);
            Lemma lemma = getLemmaByCheckingOnFactor(word, lemmaFrequency, pages);
            if (!lemma.getLemma().isEmpty()) {
                lemmaSet.add(lemma);
            }
        }

        return lemmaSet.stream().sorted(Comparator.comparing(Lemma::getFrequency))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Lemma getLemmaByCheckingOnFactor(String resultWordForm, int lemmaFrequency, List<PageEntity> pages) {
        Lemma lemma = new Lemma();
        int pageFactor = (lemmaFrequency * 100) / pages.size();

        if (pageFactor < 80) {
            lemma.setLemma(resultWordForm);
            lemma.setFrequency(lemmaFrequency);
        }

        return lemma;
    }

    private Set<PageEntity> getPages(List<IndexEntity> indexes, Set<Lemma> lemmas, SiteEntity siteEntity) {
        Set<PageEntity> pageEntities = new HashSet<>();
        for (IndexEntity indexEntity : indexes) {
            Optional<PageEntity> pageEntity = pageRepository.findById(indexEntity.getPage().getId());
            pageEntities.add(pageEntity.get());
        }
        for (Lemma lemma : lemmas.stream().skip(1).collect(Collectors.toSet())) {
            LemmaEntity lemmaEntity = lemmaRepository.getLemmaEntity(lemma.getLemma(), siteEntity);
            pageEntities.removeIf(pageEntity -> !indexRepository.existsByPageIdAndLemmaId(pageEntity.getId(),
                    lemmaEntity.getId()));
        }
        return pageEntities;
    }

    private List<DataProperties> saveDataIntoList(Set<PageEntity> pageEntities, SiteEntity siteEntity,
                                                  Set<Lemma> lemmas, String query) {
        List<DataProperties> dataPropertiesList = new ArrayList<>();
        float maxAbsoluteRelevance = getMaxPageRelevance(pageEntities, lemmas);
        for (PageEntity pageEntity : pageEntities) {
            DataProperties dataProperties = new DataProperties();
            Link link = editSiteAndUri(siteEntity.getUrl(), pageEntity.getPath());
            dataProperties.setSite(link.getSite());
            dataProperties.setSiteName(siteEntity.getName());
            dataProperties.setUri(link.getUri());
            dataProperties.setTitle(getPageTitle(pageEntity.getPath()));
            dataProperties.setSnippet(getSnippetFromPageContent(pageEntity, lemmas, query));
            dataProperties.setRelevance(calculatePageRelevance(pageEntity, maxAbsoluteRelevance, lemmas, siteEntity));
            dataPropertiesList.add(dataProperties);
        }

        return dataPropertiesList;
    }

    private Link editSiteAndUri(String site, String uri) {
        Link link = new Link();
        StringBuilder editedSite = new StringBuilder(site);
        String finalSite = editedSite.toString();
        if (site.endsWith("/")) {
            finalSite = editedSite.deleteCharAt(site.length() - 1).toString();
        }

        if (site.contains("www.")) {
            finalSite = editedSite.toString().replaceFirst("w{3}\\.", "");
        }

        String finalUri = uri.replace(finalSite, "");
        link.setSite(finalSite);
        link.setUri(finalUri);
        return link;
    }

    private String getPageTitle(String page) {
        String title;
        try {
            Thread.sleep(100);
            Document doc = Jsoup.connect(page)
                    .ignoreContentType(true)
                    .execute().parse();
            title = doc.title();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return title;
    }

    private String getSnippetFromPageContent(PageEntity pageEntity, Set<Lemma> lemmas, String query) {
        StringBuilder snippet = new StringBuilder();

        if (pageEntity.getContent().equals(query)) {
            snippet.append("<b>").append(pageEntity.getContent()).append("</b>");
        } else {
            for (Lemma lemma : lemmas) {
                String content = pageEntity.getContent().replaceAll("<(.*?)+>", "").trim();
                snippet = getAndEditContent(lemma, snippet, content);
            }
        }
        return checkOnSnippetLength(snippet.toString());
    }

    private StringBuilder getAndEditContent(Lemma lemma, StringBuilder snippet, String content) {
        String[] words = lemmaConverter.getSplitContentIntoWords(content);
        for (String word : words) {
            List<String> wordBaseForms = lemmaConverter.getReturnWordIntoBaseForm(word);
            if (!wordBaseForms.isEmpty()) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                if (lemma.getLemma().equals(resultWordForm)) {
                    snippet = convertContentIntoSnippet(word, content, snippet);
                    break;
                }
            }
        }

        return snippet;
    }

    private StringBuilder convertContentIntoSnippet(String word, String content, StringBuilder snippet) {
        if (!snippet.toString().contains(word)) {
            Pattern pattern = Pattern.compile("\\b.{0,50}" + word + ".{0,50}\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(content.toLowerCase());
            while (matcher.find()) {
                String fragmentText = content.toLowerCase().substring(matcher.start(), matcher.end());
                String text = "..." + fragmentText + "... ";
                text = Pattern.compile(word, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).
                        matcher(text).replaceAll("<b>" + word + "</b>");
                snippet.append(text);
            }
        } else {
            snippet = new StringBuilder(snippet.toString().replaceAll(word,
                    "<b>" + word + "</b>"));
        }

        return snippet;
    }

    private String checkOnSnippetLength(String snippet) {
        if (snippet.length() > 300) {
            String snippetOnRemove = snippet.substring(299);
            snippet = snippet.replaceAll(Pattern.quote(snippetOnRemove), "") + " ...";
            return snippet;
        }
        return snippet;
    }

    private float getMaxPageRelevance(Set<PageEntity> pageEntities, Set<Lemma> lemmas) {
        float max = 0;

        for (PageEntity pageEntity : pageEntities) {
            float totalRank = getRankSumForPage(lemmas, pageEntity);
            if (max < totalRank) {
                max = totalRank;
            }
        }

        return max;
    }

    private float getRankSumForPage(Set<Lemma> lemmas, PageEntity pageEntity) {
        float rank = 0;
        for (Lemma lemma : lemmas) {
            LemmaEntity lemmaEntity = lemmaRepository.getLemmaEntity(lemma.getLemma(), pageEntity.getSite());
            IndexEntity indexEntity = indexRepository.findByLemmaIdAndPageId(lemmaEntity.getId(), pageEntity.getId());
            rank += indexEntity.getRank();
        }

        return rank;
    }

    private float calculatePageRelevance(PageEntity pageEntity, float maxAbsoluteRelevance,
                                         Set<Lemma> lemmas, SiteEntity siteEntity) {
        float rank = 0;
        for (Lemma lemma : lemmas) {
            LemmaEntity lemmaEntity = lemmaRepository.getLemmaEntity(lemma.getLemma(), siteEntity);
            IndexEntity indexEntity = indexRepository.findByLemmaIdAndPageId(lemmaEntity.getId(), pageEntity.getId());
            rank += indexEntity.getRank();
        }
        return rank / maxAbsoluteRelevance;
    }
}

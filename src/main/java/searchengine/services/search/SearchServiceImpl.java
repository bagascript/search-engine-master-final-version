package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponse;
import searchengine.dto.search.DataProperties;
import searchengine.dto.search.Link;
import searchengine.lemma.LemmaConverter;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {
    private static final int SNIPPET_MAX_SIZE = 260;
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

        for (Site site : sites.getSites()) {
            ApiResponse apiResponse = searchForOneSite(query, site.getUrl());
            apiResponseList.add(apiResponse);
            totalError.add(apiResponse.getError());
        }

        if (apiResponseList.stream().anyMatch(s -> !s.isResult())) {
            totalApiResponse = sendNegativeResponse(totalError);
        } else {
            for (ApiResponse apiResponse : apiResponseList) {
                if (apiResponse.getData().isEmpty()) {
                    continue;
                }
                totalCount += apiResponse.getCount();
                totalDataProperties.addAll(apiResponse.getData());
            }

            totalApiResponse = sendPositiveResponse(totalCount, totalDataProperties);
        }
        return totalApiResponse;
    }

    private ApiResponse getFoundResultsResponse(String query, SiteEntity siteEntity) {
        ApiResponse apiResponse;
        List<DataProperties> dataPropertiesList;
        String[] words = lemmaConverter.splitContentIntoWords(query);
        Set<LemmaEntity> resultWordsSet = editQueryWords(words, siteEntity);
        if (resultWordsSet.isEmpty()) {
            dataPropertiesList = new ArrayList<>();
            apiResponse = new ApiResponse(true, 0, dataPropertiesList);
        } else {
            Set<LemmaEntity> lemmas = getLemmasSet(resultWordsSet);
            String firstLemma = lemmas.stream().findFirst().orElseThrow().getLemma();
            int lemmaId = lemmaRepository.getLemmaEntity(firstLemma, siteEntity).getId();
            List<IndexEntity> indexes = indexRepository.findAllByLemmaId(lemmaId);
            Set<PageEntity> pageEntities = getPages(indexes, lemmas.stream().skip(1).collect(Collectors.toSet()));
            if (pageEntities.isEmpty()) {
                dataPropertiesList = new ArrayList<>();
                apiResponse = new ApiResponse(true, 0, dataPropertiesList);
            } else {
                dataPropertiesList = saveDataIntoList(pageEntities, lemmas);
                apiResponse = new ApiResponse(true, dataPropertiesList.size(),
                        dataPropertiesList.stream()
                                .sorted(Collections.reverseOrder(Comparator.comparing(DataProperties::getRelevance)))
                                .collect(Collectors.toList()));
            }
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
            totalApiResponse = new ApiResponse(true, totalDataProperties.size(),
                    totalDataProperties.stream()
                            .sorted(Collections.reverseOrder(Comparator.comparing(DataProperties::getRelevance)))
                            .collect(Collectors.toList()));
        }
        return totalApiResponse;
    }

    private Set<LemmaEntity> editQueryWords(String[] words, SiteEntity siteEntity) {
        Set<LemmaEntity> lemmaSet = new HashSet<>();
        Set<String> resultWordFormSet = new HashSet<>();
        for (String word : words) {
            List<String> wordBaseForms = lemmaConverter.returnWordIntoBaseForm(word);
            if (!wordBaseForms.isEmpty()) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                resultWordFormSet.add(resultWordForm);
            }
        }
        for (String word : resultWordFormSet) {
            if (!lemmaRepository.existsByLemmaAndSiteId(word, siteEntity.getId())) {
                return new HashSet<>();
            } else {
                LemmaEntity lemmaEntity = getLemmaByCheckingOnFactor(word, siteEntity);
                if (lemmaEntity != null) {
                    lemmaSet.add(lemmaEntity);
                } else {
                    return new HashSet<>();
                }
            }
        }
        return lemmaSet;
    }

    private LemmaEntity getLemmaByCheckingOnFactor(String resultWordForm, SiteEntity siteEntity) {
        LemmaEntity lemma;
        LemmaEntity lemmaEntity = lemmaRepository.getLemmaEntity(resultWordForm, siteEntity);
        int sitePagesNumber = pageRepository.countAllPagesBySiteId(siteEntity);

        int pageFactor = (lemmaEntity.getFrequency() * 100) / sitePagesNumber;
        if (pageFactor < 80) {
            lemma = lemmaEntity;
        } else {
            lemma = null;
        }
        return lemma;
    }

    private Set<LemmaEntity> getLemmasSet(Set<LemmaEntity> lemmaSet) {
        return lemmaSet.stream().sorted(Comparator.comparing(LemmaEntity::getFrequency)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<PageEntity> getPages(List<IndexEntity> indexes, Set<LemmaEntity> lemmas) {
        Set<PageEntity> pageEntities = new HashSet<>();
        for (IndexEntity indexEntity : indexes) {
            int pageId = indexEntity.getPage().getId();
            PageEntity pageEntity = pageRepository.findById(pageId);
            pageEntities.add(pageEntity);
        }
        for (LemmaEntity lemmaEntity : lemmas) {
            pageEntities.removeIf(pageEntity -> !indexRepository.existsByPageIdAndLemmaId(pageEntity.getId(),
                    lemmaEntity.getId()));
        }
        return pageEntities;
    }

    private List<DataProperties> saveDataIntoList(Set<PageEntity> pageEntities, Set<LemmaEntity> lemmas) {
        List<DataProperties> dataPropertiesList = new ArrayList<>();
        float maxAbsoluteRelevance = getMaxPageRelevance(pageEntities, lemmas);
        for (PageEntity pageEntity : pageEntities) {
            DataProperties dataProperties = new DataProperties();
            Link link = editPageProperties(pageEntity.getSite().getUrl(), pageEntity.getPath(), pageEntity.getContent());
            dataProperties.setSiteName(pageEntity.getSite().getName());
            dataProperties.setSite(link.getSite());
            dataProperties.setUri(link.getUri());
            dataProperties.setTitle(link.getTitle());
            dataProperties.setRelevance(calculatePageRelevance(pageEntity, maxAbsoluteRelevance, lemmas));
            dataProperties.setSnippet(getSnippetFromPageContent(link.getContent(), lemmas));
            if (dataProperties.getSnippet().length() <= 3) {
                continue;
            }
            dataPropertiesList.add(dataProperties);
        }
        return dataPropertiesList;
    }

    private String getSnippetFromPageContent(String content, Set<LemmaEntity> lemmas) {
        String result = "";
        StringBuilder snippet = new StringBuilder();
        String[] words = lemmaConverter.splitContentIntoWords(content);
        Set<String> commonWords = new HashSet<>();
        for (LemmaEntity lemmaEntity : lemmas) {
            for (String word : words) {
                List<String> wordBaseForms = lemmaConverter.returnWordIntoBaseForm(word);
                if (!wordBaseForms.isEmpty()) {
                    String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                    if (lemmaEntity.getLemma().equals(resultWordForm) && !commonWords.contains(word)) {
                        commonWords.add(word);
                        result = getSnippetFinalVersion(snippet, word, content.toLowerCase()).toString();
                    }
                }
            }
        }
        return checkOnSnippetLength(result, commonWords, content.toLowerCase());
    }

    private StringBuilder getSnippetFinalVersion(StringBuilder snippet, String word, String content) {
        if (!snippet.toString().contains(word)) {
            Pattern pattern = Pattern.compile("\\b.{0,20}" + word + ".{0,80}\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String fragmentText = content.substring(matcher.start(), matcher.end());
                String text = "..." + fragmentText;
                snippet.append(text);
            }
        }
        return snippet;
    }

    public String checkOnSnippetLength(String snippet, Set<String> commonWords, String content) {
        StringBuilder builder = new StringBuilder(snippet);
        String finalSnippet = snippet;
        if (snippet.length() < SNIPPET_MAX_SIZE) {
            String editedSnippet = snippet.substring(3).concat("...");
            int indexDots = editedSnippet.indexOf("...");
            String firstFragment = editedSnippet.substring(0, indexDots);

            int lastSnippetIndexInContent = content.lastIndexOf(firstFragment);
            int extraIndexAmount = lastSnippetIndexInContent + (SNIPPET_MAX_SIZE - snippet.length());
            String finalVersion = content.substring(lastSnippetIndexInContent, extraIndexAmount);
            finalSnippet = builder.replace(indexDots, indexDots + 3, finalVersion).toString();
        }
        for (String commonWord : commonWords) {
            if (!finalSnippet.contains("<b>" + commonWord + "</b>")) {
                finalSnippet = finalSnippet.replaceAll(commonWord, "<b>" + commonWord + "</b>");
            }
        }
        if (finalSnippet.length() > SNIPPET_MAX_SIZE) {
            finalSnippet = finalSnippet.substring(0, SNIPPET_MAX_SIZE);
        }

        return finalSnippet.concat("...");
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

    private float calculatePageRelevance(PageEntity pageEntity, float maxAbsoluteRelevance, Set<LemmaEntity> lemmas) {
        float rank = getRankSumForPage(lemmas, pageEntity);
        return rank / maxAbsoluteRelevance;
    }

    private float getMaxPageRelevance(Set<PageEntity> pageEntities, Set<LemmaEntity> lemmas) {
        float max = 0;
        for (PageEntity pageEntity : pageEntities) {
            float totalRank = getRankSumForPage(lemmas, pageEntity);
            if (max < totalRank) {
                max = totalRank;
            }
        }
        return max;
    }

    private float getRankSumForPage(Set<LemmaEntity> lemmas, PageEntity pageEntity) {
        float rank = 0;
        for (LemmaEntity lemmaEntity : lemmas) {
            IndexEntity indexEntity = indexRepository.findByLemmaIdAndPageId(lemmaEntity.getId(), pageEntity.getId());
            rank += indexEntity.getRank();
        }
        return rank;
    }
}

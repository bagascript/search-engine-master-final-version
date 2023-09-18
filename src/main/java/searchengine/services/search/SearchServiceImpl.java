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
        long start = System.currentTimeMillis();
        ApiResponse apiResponse;
        List<DataProperties> dataPropertiesList;
        String[] words = lemmaConverter.getSplitContentIntoWords(query);
        Set<LemmaEntity> resultWordsSet = editQueryWords(words, siteEntity);
        if (resultWordsSet.isEmpty()) {
            dataPropertiesList = new ArrayList<>();
            apiResponse = new ApiResponse(true, 0, dataPropertiesList);
        } else {
            Set<LemmaEntity> lemmas = getLemmasSet(resultWordsSet);
            String firstLemma = lemmas.stream().findFirst().get().getLemma();
            int lemmaId = lemmaRepository.getLemmaEntity(firstLemma, siteEntity).getId();
            List<IndexEntity> indexes = indexRepository.findAllByLemmaId(lemmaId);
            Set<PageEntity> pageEntities = getPages(indexes, lemmas.stream().skip(1).collect(Collectors.toSet()), siteEntity);
            var debug3 = (double) (System.currentTimeMillis() - start);
            System.out.println();
            if (pageEntities.isEmpty()) {
                dataPropertiesList = new ArrayList<>();
                apiResponse = new ApiResponse(true, 0, dataPropertiesList);
            } else {
                dataPropertiesList = saveDataIntoList(pageEntities, lemmas, query);
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

    //TODO: Подлежит оптимизации!!!
    private Set<LemmaEntity> editQueryWords(String[] words, SiteEntity siteEntity) {
        Set<LemmaEntity> lemmaSet = new HashSet<>();
        Set<String> resultWordFormSet = new HashSet<>();
        for (String word : words) {
            List<String> wordBaseForms = lemmaConverter.getReturnWordIntoBaseForm(word);
            if (!wordBaseForms.isEmpty()) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                resultWordFormSet.add(resultWordForm);
            }
        }
        for(String word : resultWordFormSet) {
            if(!lemmaRepository.existsByLemmaAndSiteId(word, siteEntity.getId())) {
                return new HashSet<>();
            } else {
                lemmaSet.addAll(getLemmaByCheckingOnFactor(word, siteEntity));
            }
        }
        return lemmaSet;
    }

    private Set<LemmaEntity> getLemmasSet(Set<LemmaEntity> lemmaSet) {
        return lemmaSet.stream().sorted(Comparator.comparing(LemmaEntity::getFrequency)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<LemmaEntity> getLemmaByCheckingOnFactor(String resultWordForm, SiteEntity siteEntity) {
        Set<LemmaEntity> lemmaSet = new HashSet<>();
        int sitePagesNumber = pageRepository.countAllPagesBySiteId(siteEntity);
        LemmaEntity lemmaEntity = lemmaRepository.getLemmaEntity(resultWordForm, siteEntity);
        int pageFactor = (lemmaEntity.getFrequency() * 100) / sitePagesNumber;

        if (pageFactor < 80) {
            lemmaSet.add(lemmaEntity);
        }
        return lemmaSet;
    }

    private Set<PageEntity> getPages(List<IndexEntity> indexes, Set<LemmaEntity> lemmas, SiteEntity siteEntity) {
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

    private List<DataProperties> saveDataIntoList(Set<PageEntity> pageEntities, Set<LemmaEntity> lemmas, String query) {
        List<DataProperties> dataPropertiesList = new ArrayList<>();
        float maxAbsoluteRelevance = getMaxPageRelevance(pageEntities, lemmas);
        for (PageEntity pageEntity : pageEntities) {
            DataProperties dataProperties = new DataProperties();
            long start = System.currentTimeMillis();
            Link link = editPageProperties(pageEntity.getSite().getUrl(), pageEntity.getPath(), pageEntity.getContent());
            var debug4 = (double) (System.currentTimeMillis() - start);
            System.out.println();
            dataProperties.setSiteName(pageEntity.getSite().getName());
            dataProperties.setSite(link.getSite());
            dataProperties.setUri(link.getUri());
            dataProperties.setTitle(link.getTitle());
            dataProperties.setRelevance(calculatePageRelevance(pageEntity, maxAbsoluteRelevance, lemmas));
            dataProperties.setSnippet(getSnippetFromPageContent(query, link.getContent()));
            if (dataProperties.getSnippet().length() < 4) {
                continue;
            }
            dataPropertiesList.add(dataProperties);
        }

        return dataPropertiesList;
    }

    private String getSnippetFromPageContent(String query, String content) {
        StringBuilder finaSnippetVersion = new StringBuilder();

        String editedContent = content.toLowerCase();
        String [] words = query.toLowerCase().split("\\s+");
        for(String word : words) {
            Pattern pattern = Pattern.compile("\\b.{0,75}" + word + ".{0,200}\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(editedContent);
            if(!finaSnippetVersion.toString().contains(word)) {
                while (matcher.find()) {
                    String fragmentText = content.toLowerCase().substring(matcher.start(), matcher.end());
                    String text = "..." + fragmentText;
                    text = Pattern.compile(word, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).
                            matcher(text).replaceAll("<b>" + word + "</b>");
                    finaSnippetVersion.append(text);
                }
            } else {
                finaSnippetVersion = new StringBuilder(finaSnippetVersion.toString().replaceAll(word, "<b>" + word + "</b>"));
            }
        }
        return checkOnSnippetLength(finaSnippetVersion.toString(), words);
    }


    private String checkOnSnippetLength(String snippet, String [] words) {
        String editedSnippet = snippet;
        if (words.length > 1) {
            String firstWord = Arrays.stream(words).findFirst().get();
            int i = editedSnippet.indexOf(firstWord);
            String content = editedSnippet.substring(0, i + firstWord.length() + 4);
            for(String word : Arrays.stream(words).skip(1).collect(Collectors.toSet())) {
                content = content.concat("..." + editedSnippet.substring(editedSnippet.indexOf(word) - 3));
            }
            editedSnippet = content;
        }

        if(editedSnippet.length() > 280) {
            editedSnippet = editedSnippet.replaceAll(Pattern.quote(editedSnippet.substring(279)), "");
        }

        return editedSnippet.concat("...");
    }

    private Link editPageProperties(String siteURL, String uri, String content) {
        String finalSiteURLVersion = lemmaConverter.getEditSiteURL(siteURL);
        return getFixedPageProperties(finalSiteURLVersion, uri, content);
    }

    //TODO: Подлежит оптимизации!!!
    private Link getFixedPageProperties(String siteURL, String uri, String pageContent) {
        Link link = new Link();
        String url = siteURL.concat(uri);
        String title;
        String content;
        try {
            Thread.sleep(100);
            Document doc = Jsoup.connect(url)
                    .userAgent("ParSearchBot")
                    .referrer("http://www.google.com")
                    .ignoreContentType(true)
                    .execute().parse();
            title = doc.title();
            content = Jsoup.parse(pageContent).text();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        link.setSite(siteURL);
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

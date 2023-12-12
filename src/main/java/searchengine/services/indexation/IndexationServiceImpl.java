package searchengine.services.indexation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.*;
import searchengine.model.PageEntity;
import searchengine.proccessing.SiteRunnable;
import searchengine.dto.response.ApiResponses;
import searchengine.lemma.LemmaConverter;
import searchengine.model.SiteEntity;
import searchengine.model.enums.StatusType;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.dto.response.ServerResponses;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static searchengine.proccessing.LinkFinder.urlList;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexationServiceImpl implements IndexationService {
    public static boolean isIndexationRunning = false;
    public static boolean isItNewIndexationStart = false;
    private static boolean isStartIndexingMethodActive = false;

    private final LemmaConverter lemmaConverter;
    private final SitesList sites;

    private ExecutorService executorService;

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final IndexRepository indexRepository;

    @Override
    public ApiResponses startIndexingApiResponse() {
        isStartIndexingMethodActive = true;
        if (isIndexationRunning()) {
            return new ApiResponses(false, ServerResponses.INDEXATION_IS_ALREADY_RUNNING_ERROR_TEXT);
        } else {
            List<Site> siteList = sites.getSites();
            executorService = Executors.newCachedThreadPool();
            cleanDataBeforeIndexing();
            for (Site site : siteList) {
                SiteEntity siteEntity = setIndexingStatusSite(site);
                isIndexationRunning = true;
                parseSite(siteEntity);
            }
            isItNewIndexationStart = false;
            executorService.shutdown();
        }
        return new ApiResponses(true);
    }

    @Override
    public ApiResponses stopIndexingApiResponse() {
        isStartIndexingMethodActive = false;
        if (!isIndexationRunning()) {
            return new ApiResponses(false, ServerResponses.INDEXATION_IS_NOT_RUNNING_ERROR_TEXT);
        } else {
            List<SiteEntity> siteList = siteRepository.findAllByStatus(StatusType.INDEXING);
            for (SiteEntity site : siteList) {
                isIndexationRunning = false;
                executorService.shutdownNow();
                siteRepository.updateOnFailed(site.getId(), StatusType.FAILED, ServerResponses.INDEXATION_IS_STOPPED_BY_USER_TEXT);
            }
            return new ApiResponses(true);
        }
    }

    @Override
    public ApiResponses indexPageApiResponse(String url) {
        if (url.isEmpty()) {
            return new ApiResponses(false, ServerResponses.URL_EMPTY_ERROR_TEXT);
        } else {
            if (isUrlStartingWithSitePath(url)) {
                parsePage(url);
                return new ApiResponses(true);
            } else {
                return new ApiResponses(false, ServerResponses.INVALID_URL_ERROR_TEXT);
            }
        }
    }

    private void parseSite(SiteEntity siteEntity) {
        if (isUrlValid(siteEntity.getUrl())) {
            executorService.submit(new SiteRunnable(siteEntity, siteRepository, pageRepository,
                    lemmaRepository, indexRepository, lemmaConverter, sites));
        } else {
            setFailedStatusSite(siteEntity);
        }
    }

    private void parsePage(String url) {
        Connection.Response response;
        Document document;
        try {
            Thread.sleep(300);
            response = Jsoup.connect(url).userAgent(sites.getUserAgent())
                    .referrer(sites.getReferrer()).execute();
            document = response.parse();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        deletePageData(url);
        saveAndFilterPageContent(response, document, url);
    }

    private boolean isIndexationRunning() {
        if (isIndexationRunning) {
            if (isStartIndexingMethodActive) {
                List<SiteEntity> indexedSiteList = siteRepository.findAllByStatus(StatusType.INDEXING);
                return indexedSiteList.size() == sites.getSites().size();
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private void cleanDataBeforeIndexing() {
        isItNewIndexationStart = true;
        indexRepository.deleteAllInBatch();
        lemmaRepository.deleteAllInBatch();
        pageRepository.deleteAllInBatch();
        siteRepository.deleteAllInBatch();
        urlList.clear();
    }

    private SiteEntity setIndexingStatusSite(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setLastError(null);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        return siteEntity;
    }

    private void setFailedStatusSite(SiteEntity siteEntity) {
        siteRepository.updateStatusTime(siteEntity.getId());
        siteRepository.updateOnFailed(siteEntity.getId(), StatusType.FAILED, ServerResponses.SITE_IS_NOT_AVAILABLE_ERROR_TEXT);
    }

    private boolean isUrlValid(String url) {
        UrlValidator validator = new UrlValidator();
        return validator.isValid(url);
    }

    private void saveAndFilterPageContent(Connection.Response response, Document document, String url) {
        String siteUrl = sites.getSites().stream().filter(site ->
                editSiteUrl(url).startsWith(
                        editSiteUrl(site.getUrl()))).findFirst().orElseThrow().getUrl();
        SiteEntity siteEntity = siteRepository.findByUrl(siteUrl);
        String content = document.html();
        int statusCode = response.statusCode();

        String finalUrlVersion = convertURLIntoURI(url);
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(finalUrlVersion);
        pageEntity.setCode(statusCode);
        pageEntity.setContent(content);
        pageEntity.setSite(siteEntity);
        pageRepository.saveAndFlush(pageEntity);

        String pageContent = pageRepository.getContentByPath(pageEntity.getPath());
        lemmaConverter.filterPageContent(pageContent, pageEntity);
    }

    private void deletePageData(String url) {
        String finalUrlVersion = convertURLIntoURI(url);
        if (pageRepository.existsByPath(finalUrlVersion)) {
            PageEntity pageEntity = pageRepository.findByPath(finalUrlVersion);

            List<Integer> indexIds = indexRepository.findIndexesByPageId(pageEntity);
            String content = pageRepository.getContentByPath(finalUrlVersion);
            String finalContent = Jsoup.parse(content).text();
            for (int indexId : indexIds) {
                indexRepository.deleteById(indexId);
            }

            if (!finalContent.isEmpty()) {
                lemmaConverter.deleteLemmas(finalContent, pageEntity);
                pageRepository.delete(pageEntity);
            }
        }
    }

    private String convertURLIntoURI(String url) {
        String editedUrl = editSiteUrl(url);
        String siteUrl = sites.getSites().stream().filter(site -> {
            String editedSiteUrl = editSiteUrl(site.getUrl());
            return editedUrl.contains(editedSiteUrl);
        }).findFirst().orElseThrow().getUrl();

        String finalSite = lemmaConverter.editSiteURL(siteUrl);
        return editedUrl.replace(finalSite, "");
    }

    private boolean isUrlStartingWithSitePath(String url) {
        return sites.getSites().stream().anyMatch(site -> {
            String editedSiteUrl = editSiteUrl(site.getUrl());
            String editedUrl = editSiteUrl(url);
            return editedUrl.startsWith(editedSiteUrl);
        });
    }

    public static String editSiteUrl(String siteURL) {
        String editedSiteUrl = siteURL.replaceFirst("w{3}\\.", "");
        return siteURL.contains("www.") ? editedSiteUrl : siteURL;
    }
}

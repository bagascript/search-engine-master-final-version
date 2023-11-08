package searchengine.services.indexation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.*;
import searchengine.dto.indexation.IndexationServiceComponents;
import searchengine.dto.indexation.SiteRunnable;
import searchengine.dto.response.ApiResponse;
import searchengine.lemma.LemmaConverter;
import searchengine.model.SiteEntity;
import searchengine.model.enums.StatusType;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexationServiceImpl implements IndexationService {
    public static boolean isIndexationRunning = false;
    private static boolean isStartIndexingMethodActive = false;

    private static final String INVALID_URL_ERROR_TEXT = "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
    private static final String INDEXATION_IS_ALREADY_RUNNING_ERROR_TEXT = "Индексация уже запущена";
    private static final String INDEXATION_IS_STOPPED_BY_USER_TEXT = "Индексация остановлена пользователем";
    private static final String INDEXATION_IS_NOT_RUNNING_ERROR_TEXT = "Индексация не запущена! Обновите страницу";
    private static final String URL_EMPTY_ERROR_TEXT = "Страница не указана";

    private final LemmaConverter lemmaConverter;
    private final IndexationServiceComponents indexationServiceComponents;
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
    public ApiResponse startIndexingApiResponse() {
        isStartIndexingMethodActive = true;
        if (isIndexationRunning()) {
            return new ApiResponse(false, INDEXATION_IS_ALREADY_RUNNING_ERROR_TEXT);
        } else {
            List<Site> siteList = sites.getSites();
            executorService = Executors.newCachedThreadPool();
            indexationServiceComponents.cleanDataBeforeIndexing();
            for (Site site : siteList) {
                SiteEntity siteEntity = indexationServiceComponents.setIndexingStatusSite(site);
                isIndexationRunning = true;
                parseSite(siteEntity);
            }
            executorService.shutdown();
        }
        return new ApiResponse(true);
    }

    @Override
    public ApiResponse stopIndexingApiResponse() {
        isStartIndexingMethodActive = false;
        if (!isIndexationRunning()) {
            return new ApiResponse(false, INDEXATION_IS_NOT_RUNNING_ERROR_TEXT);
        } else {
            List<SiteEntity> siteList = siteRepository.findAllByStatus(StatusType.INDEXING);
            for (SiteEntity site : siteList) {
                isIndexationRunning = false;
                executorService.shutdownNow();
                siteRepository.updateOnFailed(site.getId(), StatusType.FAILED, INDEXATION_IS_STOPPED_BY_USER_TEXT);
            }
            return new ApiResponse(true);
        }
    }

    @Override
    public ApiResponse indexPageApiResponse(String url) {
        if (url.isEmpty()) {
            return new ApiResponse(false, URL_EMPTY_ERROR_TEXT);
        } else {
            if (isUrlStartingWithSitePath(url)) {
                parsePage(url);
                return new ApiResponse(true);
            } else {
                return new ApiResponse(false, INVALID_URL_ERROR_TEXT);
            }
        }
    }

    private void parseSite(SiteEntity siteEntity) {
        if (indexationServiceComponents.isUrlValid(siteEntity.getUrl())) {
            executorService.submit(new SiteRunnable(siteEntity, siteRepository, pageRepository,
                    lemmaRepository, indexRepository, lemmaConverter, indexationServiceComponents, sites));
        } else {
            indexationServiceComponents.setFailedStatusSite(siteEntity);
        }
    }

    private void parsePage(String url) {
        Connection.Response response;
        Document document;
        try {
            Thread.sleep(300);
            response = Jsoup.connect(url).userAgent(sites.getUserAgent()).referrer(sites.getReferrer()).execute();
            document = response.parse();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        indexationServiceComponents.deletePageData(url);
        executorService = Executors.newCachedThreadPool();
        executorService.submit(() -> indexationServiceComponents.saveAndFilterPageContent(response, document, url));
        executorService.shutdown();
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

    private boolean isUrlStartingWithSitePath(String url) {
        return sites.getSites().stream().anyMatch(site -> {
            String editedSiteUrl = indexationServiceComponents.editSiteUrl(site.getUrl());
            String editedUrl = indexationServiceComponents.editSiteUrl(url);
            return editedUrl.startsWith(editedSiteUrl);
        });
    }
}

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
    private static final String INDEXATION_IS_ALREADY_RUNNING_TEXT = "Индексация уже запущена";
    private static final String INDEXATION_IS_STOPPED_BY_USER_TEXT = "Индексация остановлена пользователем";
    private static final String INDEXATION_IS_NOT_RUNNING = "Индексация не запущена! Обновите страницу.";
    private static final String URL_EMPTY_ERROR_TEXT = "Страница не указана";

    private final SitesList sites;
    private ExecutorService executorService;

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;

    private final LemmaConverter lemmaConverter;

    private final IndexationServiceComponents indexationServiceComponents;

    @Override
    public ApiResponse startIndexingApiResponse() {
        isStartIndexingMethodActive = true;
        if (isIndexationRunning()) {
            return new ApiResponse(false, INDEXATION_IS_ALREADY_RUNNING_TEXT);
        } else {
            executorService = null;
            indexationServiceComponents.cleanDataBeforeIndexing();
            for (Site site : sites.getSites()) {
                SiteEntity siteEntity = new SiteEntity();
                indexationServiceComponents.setIndexingStatusSite(siteEntity, site);
                isIndexationRunning = true;
                parseSite(siteEntity);
            }
            return new ApiResponse(true, null);
        }
    }

    @Override
    public ApiResponse stopIndexingApiResponse() {
        isStartIndexingMethodActive = false;
        if (!isIndexationRunning()) {
            return new ApiResponse(false, INDEXATION_IS_NOT_RUNNING);
        } else {
            List<SiteEntity> resSites = siteRepository.findAllByStatus(StatusType.INDEXING);
            for (SiteEntity site : resSites) {
                isIndexationRunning = false;
                executorService.shutdownNow();
                siteRepository.updateOnFailed(site.getId(), StatusType.FAILED, INDEXATION_IS_STOPPED_BY_USER_TEXT);
            }
            return new ApiResponse(true, null);
        }
    }

    @Override
    public ApiResponse indexPageApiResponse(String url) {
        if (url.isEmpty()) {
            return new ApiResponse(false, URL_EMPTY_ERROR_TEXT);
        } else {
            if (isUrlStartingWithSite(url)) {
                Connection.Response response;
                Document document;
                try {
                    Thread.sleep(500);
                    response = Jsoup.connect(url).userAgent("ParSearchBot")
                            .referrer("http://www.google.com").execute();
                    document = response.parse();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                indexationServiceComponents.deletePageData(url);
                executorService = Executors.newCachedThreadPool();
                executorService.submit(() -> indexationServiceComponents.saveAndFilterPageContent(response, document, url));
                executorService.shutdown();
                return new ApiResponse(true);
            } else {
                return new ApiResponse(false, INVALID_URL_ERROR_TEXT);
            }
        }
    }

    private void parseSite(SiteEntity siteEntity) {
        if (!indexationServiceComponents.isUrlValid(siteEntity.getUrl())) {
            indexationServiceComponents.setFailedStatusSite(siteEntity);
        } else {
            synchronized (Executors.class) {
                if (executorService == null) {
                    executorService = Executors.newCachedThreadPool();
                }
                executorService.submit(new SiteRunnable(siteEntity, siteRepository,
                        pageRepository, lemmaConverter, indexationServiceComponents));
            }
        }
    }

    private boolean isIndexationRunning() {
        if (isIndexationRunning) {
            if (isStartIndexingMethodActive) {
                List<SiteEntity> indexedSitesList = siteRepository.findAllByStatus(StatusType.INDEXING);
                return indexedSitesList.size() == sites.getSites().size();
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private boolean isUrlStartingWithSite(String url) {
        return sites.getSites().stream().anyMatch(site -> {
            String editedSiteUrl = indexationServiceComponents.editSiteUrl(site.getUrl());
            String editedUrl = indexationServiceComponents.editSiteUrl(url);
            return editedUrl.startsWith(editedSiteUrl);
        });
    }
}

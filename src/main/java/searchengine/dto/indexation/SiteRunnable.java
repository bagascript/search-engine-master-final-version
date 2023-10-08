package searchengine.dto.indexation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import searchengine.lemma.LemmaConverter;
import searchengine.model.SiteEntity;
import searchengine.model.enums.StatusType;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

import static searchengine.services.indexation.IndexationServiceImpl.isIndexationRunning;

@Slf4j
@RequiredArgsConstructor
public class SiteRunnable implements Runnable {
    public static final Object MONITOR = new Object();
    private final SiteEntity siteEntity;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaConverter lemmaConverter;
    private final IndexationServiceComponents indexationServiceComponents;

    @Override
    public void run() {
        String siteUrl = indexationServiceComponents.editSiteUrl(siteEntity.getUrl());
        LinkFinder linkFinder = new LinkFinder(siteEntity, siteUrl.concat("/"), siteRepository, pageRepository, lemmaConverter);
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        ConcurrentHashMap<String, SiteEntity> links = forkJoinPool.invoke(linkFinder);


        if (isIndexationRunning) {
            synchronized (MONITOR) {
                updateSiteStatusOnIndexed(links);
            }
        } else {
            forkJoinPool.shutdownNow();
        }
    }

    private void updateSiteStatusOnIndexed(ConcurrentHashMap<String, SiteEntity> links) {
        for (Map.Entry<String, SiteEntity> link : links.entrySet()) {
            SiteEntity site = link.getValue();
            String finalSite = lemmaConverter.editSiteURL(site.getUrl());
            if (!pageRepository.getLastUrlBySiteId(site).equals(link.getKey().replace(finalSite, ""))) {
                continue;
            }
            siteRepository.updateOnIndexed(site.getId(), StatusType.INDEXED);
            siteRepository.updateStatusTime(site.getId());
            log.info("</Сайт " + site.getName() + " сохранен/>");
            break;
        }
    }
}
package searchengine.dto.indexation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import searchengine.config.SitesList;
import searchengine.lemma.LemmaConverter;
import searchengine.model.SiteEntity;
import searchengine.model.enums.StatusType;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.util.concurrent.ForkJoinPool;

import static searchengine.services.indexation.IndexationServiceImpl.isIndexationRunning;

@Slf4j
@RequiredArgsConstructor
public class SiteRunnable implements Runnable {
    private final SiteEntity siteUrl;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaConverter lemmaConverter;
    private final IndexationServiceComponents indexationServiceComponents;
    private final SitesList sites;

    @Override
    public void run() {
        SiteEntity site = siteRepository.findByUrl(siteUrl.getUrl());
        String siteUrl = indexationServiceComponents.editSiteUrl(site.getUrl());
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        forkJoinPool.invoke(new LinkFinder(sites, siteUrl.concat("/"), site, siteRepository, pageRepository, lemmaConverter, lemmaRepository, indexRepository));
        if (!isIndexationRunning) {
            forkJoinPool.shutdownNow();
        } else {
            siteRepository.updateOnIndexed(site.getId(), StatusType.INDEXED);
            siteRepository.updateStatusTime(site.getId());
            log.info("</Сайт " + site.getName() + " сохранен/>");
        }
    }
}
package searchengine.dto.indexation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import searchengine.lemma.LemmaConverter;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;

import static searchengine.dto.indexation.SiteRunnable.MONITOR;
import static searchengine.services.indexation.IndexationServiceImpl.isIndexationRunning;

@Slf4j
@RequiredArgsConstructor
public class LinkFinder extends RecursiveTask<ConcurrentHashMap<String, SiteEntity>> {
    public static final ConcurrentHashMap<String, Integer> uniqueUrlContainer = new ConcurrentHashMap<>();
    private final SiteEntity siteEntity;
    private final String url;

    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;

    private final LemmaConverter lemmaConverter;

    @Override
    protected ConcurrentHashMap<String, SiteEntity> compute() {
        ConcurrentHashMap<String, SiteEntity> links = new ConcurrentHashMap<>();
        ConcurrentHashMap<LinkFinder, SiteEntity> tasks = new ConcurrentHashMap<>();
        links.put(url, siteEntity);

        try {
            Thread.sleep(300);
            Document document = Jsoup.connect(url).userAgent("ParSearchBot").referrer("http://www.google.com").get();
            String content = document.html();
            int statusCode = document.connection().response().statusCode();
            saveLinkComponentsIntoDB(url, content, statusCode);
            Elements elements = document.select("a");
            elements.forEach(el -> {
                String link = el.attr("abs:href");
                boolean isLinkCorrect = link.startsWith(url) && !uniqueUrlContainer.containsKey(link) && !link.contains("#") && !link.contains("?");
                if (isLinkCorrect) {
                    LinkFinder linkFinderTask = new LinkFinder(siteEntity, link, siteRepository, pageRepository, lemmaConverter);
                    linkFinderTask.fork();
                    tasks.put(linkFinderTask, siteEntity);
                    uniqueUrlContainer.put(link, siteEntity.getId());
                    log.info("Индексируется страница номер " + uniqueUrlContainer.size() + ", сайт - " + siteEntity.getName());
                }
            });
        } catch (InterruptedException | IOException ignored) {
        }

        for (Map.Entry<LinkFinder, SiteEntity> task : tasks.entrySet()) {
            links.putAll(task.getKey().join());
        }

        return links;
    }

    private void saveLinkComponentsIntoDB(String link, String content, int statusCode) {
        SiteEntity site = siteRepository.findByUrl(siteEntity.getUrl());
        String finalSite = lemmaConverter.editSiteURL(site.getUrl());

        PageEntity pageEntity = new PageEntity();
        pageEntity.setSite(site);
        pageEntity.setPath(link.replace(finalSite, ""));
        pageEntity.setContent(content);
        pageEntity.setCode(statusCode);
        synchronized (MONITOR) {
            if (isIndexationRunning) {
                if (!pageRepository.existsByPath(pageEntity.getPath())) {
                    pageRepository.saveAndFlush(pageEntity);
                    lemmaConverter.filterPageContent(pageEntity.getContent(), pageEntity);
                    siteRepository.updateStatusTime(siteEntity.getId());
                    log.info("Сайт : " + pageEntity.getSite().getName());
                }
            }
        }
    }
}


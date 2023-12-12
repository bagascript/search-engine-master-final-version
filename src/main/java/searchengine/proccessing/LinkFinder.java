package searchengine.proccessing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import searchengine.config.SitesList;
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
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

import static searchengine.services.indexation.IndexationServiceImpl.isIndexationRunning;
import static searchengine.services.indexation.IndexationServiceImpl.isItNewIndexationStart;

@Slf4j
@RequiredArgsConstructor
public class LinkFinder extends RecursiveAction {
    private final SitesList sites;
    private final String url;
    public static volatile List<String> urlList = new ArrayList<>();
    private final SiteEntity siteEntity;
    private final LemmaConverter lemmaConverter;

    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final IndexRepository indexRepository;

    @Override
    protected void compute() {
        try {
            Thread.sleep(150);
            Document document = null;
            Thread.sleep(150);
            document = Jsoup.connect(url).userAgent(sites.getUserAgent()).referrer(sites.getReferrer()).get();
            String content = document.html();
            Connection.Response response = document.connection().response();
            int statusCode = response.statusCode();
            String finalUrlVersion = lemmaConverter.editSiteURL(siteEntity.getUrl());
            saveLinkComponentsIntoDB(url.replace(finalUrlVersion, ""), content, statusCode);
            if (siteRepository.existsById(siteEntity.getId())) parseUrl(document);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveLinkComponentsIntoDB(String link, String content, int statusCode) {
        synchronized (siteEntity) {
            if (isIndexationRunning && siteRepository.existsById(siteEntity.getId())) {
                PageEntity pageEntity = new PageEntity();
                pageEntity.setSite(siteEntity);
                pageEntity.setPath(link);
                pageEntity.setContent(content);
                pageEntity.setCode(statusCode);
                pageRepository.saveAndFlush(pageEntity);
                parseUrlContent(content, pageEntity);
                siteRepository.updateStatusTime(siteEntity.getId());
            }
        }
    }

    public void parseUrlContent(String content, PageEntity pageEntity) {
        Set<String> uniqueLemmas = new HashSet<>();
        String finalContent = Jsoup.parse(content).text();
        String[] words = lemmaConverter.splitContentIntoWords(finalContent);
        for (String word : words) {
            List<String> wordBaseForms = lemmaConverter.returnWordIntoBaseForm(word);
            if (!wordBaseForms.isEmpty()) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                int siteId = pageEntity.getSite().getId();
                if (lemmaRepository.existsByLemmaAndSiteId(resultWordForm, siteId)) {
                    updateDataIntoDB(pageEntity, resultWordForm, uniqueLemmas);
                } else if (!uniqueLemmas.contains(resultWordForm)) {
                    saveNewDataIntoDB(pageEntity, resultWordForm, uniqueLemmas);
                }
            }
        }
    }

    private void updateDataIntoDB(PageEntity pageEntity, String resultWordForm, Set<String> uniqueLemmas) {
        LemmaEntity lemmaEntity = lemmaRepository.getLemmaEntity(resultWordForm, pageEntity.getSite());
        if (!isItNewIndexationStart && siteRepository.existsById(pageEntity.getSite().getId())) {
            if (!uniqueLemmas.contains(resultWordForm)) {
                lemmaRepository.updateFrequency(lemmaEntity.getId(), lemmaEntity.getFrequency() + 1);
                uniqueLemmas.add(resultWordForm);
            }
            indexLemma(pageEntity, lemmaEntity);
        }
    }

    private void saveNewDataIntoDB(PageEntity pageEntity, String resultWordForm, Set<String> uniqueLemmas) {
        LemmaEntity lemmaEntity = new LemmaEntity();
        lemmaEntity.setLemma(resultWordForm);
        lemmaEntity.setFrequency(1);
        lemmaEntity.setSite(pageEntity.getSite());
        if (!isItNewIndexationStart && siteRepository.existsById(pageEntity.getSite().getId())) {
            lemmaRepository.saveAndFlush(lemmaEntity);
            log.info("Новая лемма '" + lemmaEntity.getLemma() + "' была добавлена");
            indexLemma(pageEntity, lemmaEntity);
            uniqueLemmas.add(resultWordForm);
        }
    }

    private void indexLemma(PageEntity pageEntity, LemmaEntity lemmaEntity) {
        if (indexRepository.existsByPageIdAndLemmaId(pageEntity.getId(), lemmaEntity.getId())) {
            IndexEntity index = indexRepository.findByLemmaIdAndPageId(lemmaEntity.getId(), pageEntity.getId());
            indexRepository.updateIndexRank(index.getId(), index.getRank() + 1);
        } else {
            IndexEntity indexEntity = new IndexEntity();
            indexEntity.setRank(1);
            indexEntity.setLemma(lemmaEntity);
            indexEntity.setPage(pageEntity);
            indexRepository.save(indexEntity);
        }
    }


    private void parseUrl(Document document) {
        Elements elements = document.select("body").select("a");
        List<LinkFinder> tasks = new ArrayList<>();
        elements.forEach(el -> {
            String link = el.attr("abs:href");
            boolean isLinkCorrect = link.startsWith(el.baseUri()) && !link.equals(el.baseUri())
                    && !link.contains("#") && !urlList.contains(link) && !link.contains("?");
            if (checkUrlOnValidElementType(link) && isLinkCorrect) {
                urlList.add(link);
                LinkFinder task = new LinkFinder(sites, link, siteEntity, lemmaConverter, siteRepository,
                        pageRepository, lemmaRepository, indexRepository);
                task.fork();
                tasks.add(task);
            }
        });
        tasks.forEach(ForkJoinTask::join);
    }

    private boolean checkUrlOnValidElementType(String url) {
        List<String> wrongTypeList = Arrays.asList("JPG", "gif", "gz", "jar", "jpeg", "jpg", "pdf", "png", "ppt", "pptx", "svg", "svg", "tar", "zip");
        return !wrongTypeList.contains(url.substring(url.lastIndexOf(".") + 1));
    }
}


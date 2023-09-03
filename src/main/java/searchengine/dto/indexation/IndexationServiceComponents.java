package searchengine.dto.indexation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.lemma.LemmaConverter;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.enums.StatusType;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;

import java.util.List;

import static searchengine.dto.indexation.LinkFinder.uniqueUrlContainer;


@RequiredArgsConstructor
@Slf4j
@Component
public class IndexationServiceComponents {
    private static final String SITE_IS_NOT_AVAILABLE_TEXT = "Главная страница сайта не доступна";

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final IndexRepository indexRepository;

    private final LemmaConverter lemmaConverter;

    private final SitesList sites;

    public void cleanDataBeforeIndexing() {
        List<IndexEntity> indexes = indexRepository.findAll();
        indexRepository.deleteAll(indexes);
        List<LemmaEntity> lemmas = lemmaRepository.findAll();
        lemmaRepository.deleteAll(lemmas);
        List<PageEntity> pages = pageRepository.findAll();
        pageRepository.deleteAll(pages);
        List<SiteEntity> sites = siteRepository.findAll();
        siteRepository.deleteAll(sites);
        uniqueUrlContainer.clear();
    }

    public void setIndexingStatusSite(SiteEntity siteEntity, Site site) {
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setLastError(null);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    public void setFailedStatusSite(SiteEntity siteEntity) {
        siteRepository.updateStatusTime(siteEntity.getId());
        siteRepository.updateOnFailed(siteEntity.getId(), StatusType.FAILED, SITE_IS_NOT_AVAILABLE_TEXT);
    }

    public boolean isUrlValid(String url) {
        UrlValidator validator = new UrlValidator();
        return validator.isValid(url);
    }

    public String editSiteUrl(String siteURL) {
        String editedSiteUrl = siteURL.replaceFirst("w{3}\\.", "");
        return siteURL.contains("www.") ? editedSiteUrl : siteURL;
    }

    public boolean saveAndFilterPageContent(Connection.Response response, Document document, String url) {
        SiteEntity siteUrl = siteRepository.findByUrl(sites.getSites().stream()
                .filter(site -> url.startsWith(editSiteUrl(site.getUrl())))
                .findFirst().get().getUrl());
        String content = document.html();
        int statusCode = response.statusCode();

        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(url);
        pageEntity.setCode(statusCode);
        pageEntity.setContent(content);
        pageEntity.setSite(siteUrl);
        pageRepository.saveAndFlush(pageEntity);
        lemmaConverter.getFilterPageContent(pageRepository.getContentByPath(url), pageEntity);
        return true;
    }

    public void deletePageData(String path) {
        if (pageRepository.existsByPath(path)) {
            PageEntity pageEntity = pageRepository.findByPath(path);
            List<Integer> indexIds = indexRepository.findIndexesByPageId(pageEntity);
            String content = pageRepository.getContentByPath(path).replaceAll("<(.*?)+>", "").trim();

            for (int indexId : indexIds) {
                indexRepository.deleteById(indexId);
            }

            if (!content.isEmpty()) {
                lemmaConverter.getDeleteLemmas(content, pageEntity);
                pageRepository.delete(pageEntity);
            }
        }
    }
}



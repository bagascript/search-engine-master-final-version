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
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.enums.StatusType;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;

import java.util.List;




@RequiredArgsConstructor
@Slf4j
@Component
public class IndexationServiceComponents {
    private static final String SITE_IS_NOT_AVAILABLE_ERROR_TEXT = "Главная страница сайта не доступна";

    private final LemmaConverter lemmaConverter;
    private final SitesList sites;

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final IndexRepository indexRepository;

    public void cleanDataBeforeIndexing() {
        indexRepository.deleteAllInBatch();
        lemmaRepository.deleteAllInBatch();
        pageRepository.deleteAllInBatch();
        siteRepository.deleteAllInBatch();
    }

    public SiteEntity setIndexingStatusSite(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setLastError(null);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        return siteEntity;
    }

    public void setFailedStatusSite(SiteEntity siteEntity) {
        siteRepository.updateStatusTime(siteEntity.getId());
        siteRepository.updateOnFailed(siteEntity.getId(), StatusType.FAILED, SITE_IS_NOT_AVAILABLE_ERROR_TEXT);
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
        String siteUrl = sites.getSites().stream().filter(site ->
                        url.startsWith(editSiteUrl(site.getUrl()))).findFirst().orElseThrow().getUrl();
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
        return true;
    }

    public void deletePageData(String url) {
        String finalUrlVersion = convertURLIntoURI(url);
        if (pageRepository.existsByPath(finalUrlVersion)) {
            PageEntity pageEntity = pageRepository.findByPath(finalUrlVersion);

            List<Integer> indexIds = indexRepository.findIndexesByPageId(pageEntity);
            String content = pageRepository.getContentByPath(finalUrlVersion).replaceAll("<(.*?)+>", "").trim();
            for (int indexId : indexIds) {
                indexRepository.deleteById(indexId);
            }

            if (!content.isEmpty()) {
                lemmaConverter.deleteLemmas(content, pageEntity);
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
}



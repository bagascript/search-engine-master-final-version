package searchengine.services.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.response.ApiResponses;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final LemmaRepository lemmaRepository;

    private final SitesList sites;

    private TotalStatistics totalStatistics = new TotalStatistics();
    private List<DetailedStatisticsItem> statisticsItemList = new ArrayList<>();

    @Override
    public ApiResponses getStatistics() {
        getTotalAndDetailedStatistics();
        ApiResponses apiResponses = new ApiResponses();
        StatisticsData statisticsData = getStatisticsData();
        apiResponses.setStatistics(statisticsData);
        apiResponses.setResult(true);
        return apiResponses;
    }

    private void getTotalAndDetailedStatistics() {
        List<Site> sitesList = sites.getSites();
        totalStatistics.setSites(sitesList.size());
        totalStatistics.setIndexing(true);
        for (Site site : sitesList) {
            DetailedStatisticsItem siteDetailedStatistics = new DetailedStatisticsItem(site.getUrl(), site.getName(),
                    null, LocalDateTime.now(), null, 0, 0);
            if (siteRepository.existsByUrl(siteDetailedStatistics.getUrl())) {
                siteDetailedStatistics = setSiteDataStatistic(site);
            }
            totalStatistics.setPages(totalStatistics.getPages() + siteDetailedStatistics.getPages());
            totalStatistics.setLemmas(totalStatistics.getLemmas() + siteDetailedStatistics.getLemmas());
            statisticsItemList.add(siteDetailedStatistics);
        }
    }

    private DetailedStatisticsItem setSiteDataStatistic(Site site) {
        SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
        String status = siteEntity.getStatus().toString();
        LocalDateTime statusTime = siteEntity.getStatusTime();
        String error = siteEntity.getLastError();
        int pages = pageRepository.countAllPagesBySiteId(siteEntity);
        int lemmas = lemmaRepository.countAllLemmasBySiteId(siteEntity);
        return new DetailedStatisticsItem(site.getUrl(), site.getName(), status, statusTime, error, pages, lemmas);
    }

    private StatisticsData getStatisticsData() {
        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(totalStatistics);
        statisticsData.setDetailed(statisticsItemList);

        totalStatistics = new TotalStatistics();
        statisticsItemList = new ArrayList<>();
        return statisticsData;
    }
}

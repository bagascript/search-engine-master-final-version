package searchengine.proccessing;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.dto.search.PageRelevance;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexRepository;

import java.util.*;

@Component
@RequiredArgsConstructor
public class RelevanceCalculation
{
    @Autowired
    private final IndexRepository indexRepository;

    public List<PageRelevance> searchForAllLemmaIndexes(Set<LemmaEntity> lemmas, Set<PageEntity> pages) {
        List<IndexEntity> ranks = new ArrayList<>();
        for(LemmaEntity lemma : lemmas) {
            List<IndexEntity> indexes = indexRepository.findPageRelevance(lemma.getSite(), lemma);
            ranks.addAll(indexes);
        }
        Comparator<IndexEntity> c = Comparator.comparingInt(i -> i.getPage().getId());
        ranks.sort(c);
        return calculateRelevance(pages, ranks);
    }

    private List<PageRelevance> calculateRelevance(Set<PageEntity> pageEntities, List<IndexEntity> ranks) {
        float max = 1;
        List<PageRelevance> pageRelevanceList = new ArrayList<>();
        for (PageEntity pageEntity : pageEntities) {
            float sum = 0;
            PageRelevance pageRelevance = new PageRelevance();
            for(IndexEntity indexEntity : ranks) {
                if(pageEntity.getId() == indexEntity.getPage().getId()) {
                    sum += indexEntity.getRank();
                }
            }

            pageRelevance.setPageEntity(pageEntity);
            pageRelevance.setAbsoluteRank(sum);
            pageRelevanceList.add(pageRelevance);

            if(max < sum) {
                max = sum;
            }
        }
        pageRelevanceList.get(0).setMaxRank(max);
        return pageRelevanceList;
    }
}

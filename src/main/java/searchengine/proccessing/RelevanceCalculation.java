package searchengine.proccessing;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexRepository;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class RelevanceCalculation
{
    @Autowired
    private final IndexRepository indexRepository;

    public float calculatePageRelevance(PageEntity pageEntity, float maxAbsoluteRelevance, Set<LemmaEntity> lemmas) {
        float rank = getRankSumForPage(lemmas, pageEntity);
        return rank / maxAbsoluteRelevance;
    }

    public float getMaxPageRelevance(Set<PageEntity> pageEntities, Set<LemmaEntity> lemmas) {
        float max = 0;
        for (PageEntity pageEntity : pageEntities) {
            float totalRank = getRankSumForPage(lemmas, pageEntity);
            if (max < totalRank) {
                max = totalRank;
            }
        }
        return max;
    }

    private float getRankSumForPage(Set<LemmaEntity> lemmas, PageEntity pageEntity) {
        float rank = 0;
        for (LemmaEntity lemmaEntity : lemmas) {
            IndexEntity indexEntity = indexRepository.findByLemmaIdAndPageId(lemmaEntity.getId(), pageEntity.getId());
            rank += indexEntity.getRank();
        }
        return rank;
    }
}

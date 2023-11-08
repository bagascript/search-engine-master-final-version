package searchengine.dto.search;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.lemma.LemmaConverter;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PageFinder
{
    private final LemmaConverter lemmaConverter;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final IndexRepository indexRepository;

    public Set<PageEntity> getPages(List<IndexEntity> indexes, Set<LemmaEntity> lemmas) {
        Set<PageEntity> pageEntities = new HashSet<>();
        for (IndexEntity indexEntity : indexes) {
            int pageId = indexEntity.getPage().getId();
            PageEntity pageEntity = pageRepository.findById(pageId);
            pageEntities.add(pageEntity);
        }
        for (LemmaEntity lemmaEntity : lemmas) {
            pageEntities.removeIf(pageEntity -> !indexRepository.existsByPageIdAndLemmaId(pageEntity.getId(),
                    lemmaEntity.getId()));
        }
        return pageEntities;
    }

    public Set<LemmaEntity> getLemmasSet(Set<LemmaEntity> lemmaSet) {
        return lemmaSet.stream().sorted(Comparator.comparing(LemmaEntity::getFrequency)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<LemmaEntity> editQueryWords(String[] words, SiteEntity siteEntity) {
        Set<LemmaEntity> lemmaSet = new HashSet<>();
        Set<String> resultWordFormSet = new HashSet<>();
        for (String word : words) {
            List<String> wordBaseForms = lemmaConverter.returnWordIntoBaseForm(word);
            if (!wordBaseForms.isEmpty()) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                resultWordFormSet.add(resultWordForm);
            }
        }
        for (String word : resultWordFormSet) {
            if (!lemmaRepository.existsByLemmaAndSiteId(word, siteEntity.getId())) {
                return new HashSet<>();
            } else {
                LemmaEntity lemmaEntity = getLemmaByCheckingOnFactor(word, siteEntity);
                if (lemmaEntity != null) {
                    lemmaSet.add(lemmaEntity);
                } else {
                    return new HashSet<>();
                }
            }
        }
        return lemmaSet;
    }

    private LemmaEntity getLemmaByCheckingOnFactor(String resultWordForm, SiteEntity siteEntity) {
        LemmaEntity lemma;
        LemmaEntity lemmaEntity = lemmaRepository.getLemmaEntity(resultWordForm, siteEntity);
        int sitePagesNumber = pageRepository.countAllPagesBySiteId(siteEntity);

        int pageFactor = (lemmaEntity.getFrequency() * 100) / sitePagesNumber;
        if (pageFactor < 80) {
            lemma = lemmaEntity;
        } else {
            lemma = null;
        }
        return lemma;
    }
}

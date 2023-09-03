package searchengine.lemma;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;

import java.io.IOException;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class LemmaConverter {
    public static boolean isIndexing = false;
    private static boolean isDeleted = false;

    private static final HashMap<String, HashMap<Integer, Integer>> lemmaTotalMap = new HashMap<>();

    private static final String REGEX = "[^а-яА-Яa-zA-Z\\s]";
    private static final String[] RUS_FUNCTIONAL_TYPES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};
    private static final String[] ENG_FUNCTIONAL_TYPES = new String[]{"CONJ", "PREP", "ARTICLE", "PART", "INT"};

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final IndexRepository indexRepository;


    private void filterPageContent(String content, PageEntity pageEntity) {
        String finalContentVersion = content.replaceAll("<(.*?)+>", "").trim();

        if (!finalContentVersion.isEmpty()) {
            convertContentToLemmas(finalContentVersion, pageEntity);
        }
    }

    private void deleteLemmas(String content, PageEntity pageEntity) {
        Set<String> uniqueLemmas = new HashSet<>();
        String[] words = splitContentIntoWords(content);

        for (String word : words) {
            List<String> wordBaseForms = returnWordIntoBaseForm(word);
            if (!wordBaseForms.isEmpty()) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                if (lemmaRepository.existsByLemmaAndSiteId(resultWordForm, pageEntity.getSite().getId())) {
                    searchAndDeleteLemmas(resultWordForm, pageEntity, uniqueLemmas);
                }
            }
        }
    }

    private void searchAndDeleteLemmas(String resultWordForm, PageEntity pageEntity, Set<String> uniqueLemmas) {
        SiteEntity site = pageEntity.getSite();
        if (lemmaRepository.getFrequencyByLemmaAndSiteId(resultWordForm, site) > 1
                && !uniqueLemmas.contains(resultWordForm)) {
            HashMap<Integer, Integer> siteAndFrequencyMap = new HashMap<>();
            LemmaEntity lemmaEntity = lemmaRepository.getLemmaEntity(resultWordForm, site);

            siteAndFrequencyMap.put(lemmaEntity.getSite().getId(), lemmaEntity.getFrequency() - 1);
            lemmaTotalMap.put(lemmaEntity.getLemma(), siteAndFrequencyMap);
            lemmaRepository.updateFrequency(lemmaEntity.getId(), lemmaTotalMap.get(lemmaEntity.getLemma())
                    .get(site.getId()) - 1);
            uniqueLemmas.add(resultWordForm);
            isDeleted = true;
        } else if (!uniqueLemmas.contains(resultWordForm)) {
            lemmaRepository.deleteByLemmaAndSiteId(resultWordForm, site.getId());
            uniqueLemmas.add(resultWordForm);
        }
    }

    private void convertContentToLemmas(String content, PageEntity pageEntity) {
        Set<String> uniqueLemmas = new HashSet<>();
        String[] words = splitContentIntoWords(content);

        for (String word : words) {
            isIndexing = true;
            List<String> wordBaseForms = returnWordIntoBaseForm(word);

            if (!wordBaseForms.isEmpty()) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                int siteId = pageEntity.getSite().getId();
                if (lemmaRepository.existsByLemmaAndSiteId(resultWordForm, siteId)) {
                    LemmaEntity lemmaEntity = lemmaRepository.getLemmaEntity(resultWordForm, pageEntity.getSite());
                    searchAndSaveLemmas(resultWordForm, lemmaEntity, uniqueLemmas);
                    indexLemma(pageEntity, lemmaEntity);
                } else if (!uniqueLemmas.contains(resultWordForm)) {
                    LemmaEntity lemmaEntity = getNewLemma(resultWordForm, pageEntity);
                    lemmaRepository.saveAndFlush(lemmaEntity);
                    log.info("Новая лемма '" + lemmaEntity.getLemma() + "' страницы #" + pageEntity.getId() + " была добавлена");
                    indexLemma(pageEntity, lemmaEntity);
                    uniqueLemmas.add(resultWordForm);
                }
            }
        }
        isDeleted = false;
        isIndexing = false;
    }

    private void searchAndSaveLemmas(String resultWordForm, LemmaEntity lemmaEntity, Set<String> uniqueLemmas) {
        SiteEntity site = lemmaEntity.getSite();

        if (!uniqueLemmas.contains(resultWordForm)) {
            if (isDeleted) {
                lemmaRepository.updateFrequency(lemmaEntity.getId(),
                        lemmaTotalMap.get(resultWordForm).get(site.getId()) + 1);
            } else {
                lemmaRepository.updateFrequency(lemmaEntity.getId(),
                        lemmaEntity.getFrequency() + 1);
            }

            log.info("Лемма '" + lemmaEntity.getLemma() + " была обновлена - кол-во: " + lemmaRepository.getLemmaEntity(resultWordForm, site).getFrequency());
            uniqueLemmas.add(resultWordForm);
        }
    }

    private LemmaEntity getNewLemma(String resultWordForm, PageEntity pageEntity) {
        LemmaEntity lemmaEntity = new LemmaEntity();
        lemmaEntity.setLemma(resultWordForm);
        lemmaEntity.setFrequency(1);
        lemmaEntity.setSite(pageEntity.getSite());
        log.info("Новая лемма '" + lemmaEntity.getLemma() + "' страницы #" + pageEntity.getId() + " была добавлена");
        return lemmaEntity;
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

    private String[] splitContentIntoWords(String content) {
        String[] words;

        if (content.substring(0, 1).matches(REGEX)) {
            words = content.toLowerCase(Locale.ROOT).replace(content.substring(0, 1), "")
                    .replaceAll(REGEX, " ").split("\\s+");
        } else {
            words = content.toLowerCase(Locale.ROOT).replaceAll(REGEX, " ").split("\\s+");
        }

        return words;
    }

    private List<String> returnWordIntoBaseForm(String word) {
        List<String> lemmaList = new ArrayList<>();
        try {
            if (checkLanguage(word).equals("Russian")) {
                if (!word.isEmpty() && !isRusWordFunctional(word)) {
                    List<String> baseRusForm = new RussianLuceneMorphology().getNormalForms(word);
                    lemmaList.add(baseRusForm.get(baseRusForm.size() - 1));
                }
            } else if (checkLanguage(word).equals("English")) {
                if (!word.isEmpty() && !isEngWordFunctional(word)) {
                    List<String> baseEngForm = new EnglishLuceneMorphology().getNormalForms(word);
                    lemmaList.add(baseEngForm.get(baseEngForm.size() - 1));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return lemmaList;
    }

    private String checkLanguage(String word) {
        String russianAlphabet = "[а-яА-Я]{2,}";
        String englishAlphabet = "[a-zA-Z]{2,}";

        if (word.matches(russianAlphabet)) {
            return "Russian";
        } else if (word.matches(englishAlphabet)) {
            return "English";
        } else {
            return "";
        }
    }

    private boolean isRusWordFunctional(String word) {
        List<String> morphForm;
        boolean result = false;
        try {
            morphForm = new RussianLuceneMorphology().getMorphInfo(word);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String functionalType : RUS_FUNCTIONAL_TYPES) {
            if (morphForm.get(morphForm.size() - 1).contains(functionalType) || word.length() < 3) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean isEngWordFunctional(String word) {
        List<String> morphForm;
        boolean result = false;
        try {
            morphForm = new EnglishLuceneMorphology().getMorphInfo(word);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String functionalType : ENG_FUNCTIONAL_TYPES) {
            if (morphForm.get(morphForm.size() - 1).contains(functionalType) || word.length() <= 3) {
                result = true;
                break;
            }
        }
        return result;
    }

    public void getFilterPageContent(String content, PageEntity pageEntity) {
        filterPageContent(content, pageEntity);
    }

    public void getDeleteLemmas(String content, PageEntity pageEntity) {
        deleteLemmas(content, pageEntity);
    }

    public String[] getSplitContentIntoWords(String query) {
        return splitContentIntoWords(query);
    }

    public List<String> getReturnWordIntoBaseForm(String word) {
        return returnWordIntoBaseForm(word);
    }
}
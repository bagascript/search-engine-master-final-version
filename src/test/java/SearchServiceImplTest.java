import junit.framework.TestCase;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.dto.search.CommonWord;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SearchServiceImplTest extends TestCase {
    private final RussianLuceneMorphology russianLuceneMorphology;

    {
        try {
            russianLuceneMorphology = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    String snippet;
    Set<String> commonWords;
    Set<CommonWord> commonWords2;

    @Override
    protected void setUp() throws Exception {
        snippet = ".... самостоятельный старт даёт максимальную свободу действий. начинающий ивент-менеджер может ..." +
                "и внимательным — на старте неизбежны ошибки и провалы. если их будет слишком много, это испортит репутацию..." +
                "в соцсетях. для старта важно сделать хорошее портфолио — это повышает доверие потенциальных клиентов." +
                "... кто такой менеджер маркетплейсов, чем он занимается и почему ему готовы платить 300 тысяч рублей в месяц всё о ...";
        CommonWord commonWord1 = new CommonWord();
        commonWord1.setWord("старт");
        commonWord1.setIndex(21);
        commonWord1.setLength(5);
        CommonWord commonWord2 = new CommonWord();
        commonWord2.setWord("старте");
        commonWord2.setIndex(100);
        commonWord2.setLength(6);
        CommonWord commonWord3 = new CommonWord();
        commonWord3.setWord("маркетплейсов");
        commonWord3.setIndex(139);
        commonWord3.setLength(13);
        commonWords2 = new HashSet<>(3);
        commonWords2.add(commonWord1);
        commonWords2.add(commonWord2);
        commonWords2.add(commonWord3);

        super.setUp();
    }

    String s = "... / skillbox media старт с нуля самый сложный. чтобы научиться привлекать трафик, нужно сде... " +
            "на старте. стартовать с нуля ..." +
            "менеджера маркетплейсов «за полтора месяца я продала 1100 единиц товара на wildberries»:" +
            "история марины что такое веб-сайт, как он старт работает и как созда...";

    String result = "... / skillbox media <b>старт</b> с нуля самый сложный." +
            "чтобы научиться привлекать трафик, нужно сде... на <b><b>старт</b>е</b>." +
            "<b>старт</b>овать с нуля ...менеджера <b>маркетплейсов</b> «за полтора месяца я продала" +
            "1100 единиц товара на wildberries»:история марины что такое веб-сайт, как он работает и как созда...";
    public void testAddTagsToWordInSnippet() {
        String finalSnippet = s;
        StringBuilder builder = new StringBuilder(finalSnippet);
        Comparator<CommonWord> commonWordComparator = Comparator.comparingInt(CommonWord::getLength);
        Set<CommonWord> finalCommonWordSet = commonWords2.stream()
                .sorted(commonWordComparator.reversed()).collect(Collectors.toCollection(LinkedHashSet::new));
        for(CommonWord commonWord : finalCommonWordSet) {
            String word = commonWord.getWord();
            String regex = "[^a-zа-я]" + commonWord.getWord() + "[^a-zа-я]";
            finalSnippet = finalSnippet.replaceAll(regex, " <b>" + word + "</b> ");
        }
        var res = finalSnippet;
        System.out.println();
    }

    public void testCheckOnSnippetLength() {
        int snippetHalf = snippet.length() / 2;
        String firstSnippetHalf = snippet.substring(0, snippetHalf + 1);
        String secondSnippetHalf = snippet.substring(snippetHalf);
        List<String> wordsInFirstHalf = new ArrayList<>();
        List<String> wordsInSecondHalf = new ArrayList<>(commonWords);

        filterCommonWords(wordsInFirstHalf, wordsInSecondHalf);

        String finalFirstHalfVersion = editFirstSnippetHalf(wordsInFirstHalf, firstSnippetHalf);

        String finalSecondHalfVersion = editSecondSnippetHalf(wordsInSecondHalf, secondSnippetHalf);
        System.out.println();
        String totalSnippet = finalFirstHalfVersion.concat(finalSecondHalfVersion);
        System.out.println();
    }

    private void filterCommonWords(List<String> wordsInFirstHalf, List<String> wordsInSecondHalf) {
        List<String> lemmaWords = new ArrayList<>();
        for (String commonWord : commonWords) {
            List<String> baseRusForm = russianLuceneMorphology.getNormalForms(commonWord);
            String resultWordForm = baseRusForm.get(baseRusForm.size() - 1);
            if (snippet.substring(0, (snippet.length() / 2) + 1).contains(commonWord)) {
                if (!lemmaWords.contains(resultWordForm)) lemmaWords.add(resultWordForm);

                wordsInSecondHalf.remove(commonWord);
                wordsInFirstHalf.add(commonWord);

            } else {
                if (lemmaWords.stream().anyMatch(w -> w.equals(resultWordForm))) wordsInSecondHalf.remove(commonWord);
            }
        }
    }

    private String editFirstSnippetHalf(List<String> wordsInFirstHalf, String firstSnippetHalf) {
        StringBuilder snippetFragments = new StringBuilder();
        for (int i = 0; i < wordsInFirstHalf.size(); i++) {
            int index = firstSnippetHalf.indexOf(wordsInFirstHalf.get(i));
            int secondIndex;

            int lastIndexOfWord = index + wordsInFirstHalf.get(i).length();
            if (i != wordsInFirstHalf.size() - 1) {
                secondIndex = firstSnippetHalf.indexOf(wordsInFirstHalf.get(i + 1));
            } else {
                int indexOfLastDots = firstSnippetHalf.substring(0, index).lastIndexOf("...");
                int indexOfLastSpace = firstSnippetHalf.substring(indexOfLastDots, index - 1).lastIndexOf(" ");
                int indexOfOneWordBeforeQueryWord = indexOfLastDots + indexOfLastSpace;

                snippetFragments.append(firstSnippetHalf, indexOfOneWordBeforeQueryWord, lastIndexOfWord + 20).append("...");
                break;
            }
            int diffBetweenFirstAndSecondWord = secondIndex - (lastIndexOfWord);
            if (diffBetweenFirstAndSecondWord <= 10) continue;

            snippetFragments.append(firstSnippetHalf, index, secondIndex + 1)
                    .replace(lastIndexOfWord + (diffBetweenFirstAndSecondWord / 2), secondIndex, "...");
        }
        int indexFirstWord = firstSnippetHalf.indexOf(wordsInFirstHalf.stream().findFirst().orElseThrow());
        return firstSnippetHalf.substring(0, indexFirstWord).concat(snippetFragments.toString());
    }

    private String editSecondSnippetHalf(List<String> wordsInSecondHalf, String secondSnippetHalf) {
        StringBuilder snippetFragments = new StringBuilder();
        for (int i = 0; i < wordsInSecondHalf.size(); i++) {
                int index = secondSnippetHalf.indexOf(wordsInSecondHalf.get(i));
                int secondIndex;

                int lastIndexOfWord = index + wordsInSecondHalf.get(i).length();
                if (i != wordsInSecondHalf.size() - 1) {
                    secondIndex = secondSnippetHalf.indexOf(wordsInSecondHalf.get(i + 1));
                } else {
                    int indexOfLastDots = secondSnippetHalf.substring(0, index).lastIndexOf("...");
                    int indexOfLastSpace = secondSnippetHalf.substring(indexOfLastDots, index - 1).lastIndexOf(" ");
                    int start = indexOfLastDots + indexOfLastSpace;

                    String oneWordBeforeQueryWord = secondSnippetHalf.substring(start, index).trim();
                    int indexOfOneWordBeforeQueryWord = secondSnippetHalf.indexOf(oneWordBeforeQueryWord);
                    snippetFragments.append(secondSnippetHalf,
                            indexOfOneWordBeforeQueryWord, lastIndexOfWord + 1);
                    break;
                }
                int diffBetweenFirstAndSecondWord = secondIndex - (lastIndexOfWord);
                if (diffBetweenFirstAndSecondWord <= 10) continue;

                snippetFragments.append(secondSnippetHalf, index, secondIndex + 1)
                        .replace(lastIndexOfWord + (diffBetweenFirstAndSecondWord / 2), secondIndex, "...");

        }

        return snippetFragments.toString();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}

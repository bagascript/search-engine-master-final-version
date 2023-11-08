package searchengine.dto.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import searchengine.lemma.LemmaConverter;
import searchengine.model.LemmaEntity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SnippetCreation
{
    private static final int SNIPPET_MAX_SIZE = 280;

    private final LemmaConverter lemmaConverter;

    public String getSnippetFromPageContent(String content, Set<LemmaEntity> lemmas) {
        String snippetSketch = "";
        StringBuilder snippet = new StringBuilder();
        String[] words = lemmaConverter.splitContentIntoWords(content);

        List<CommonWord> commonWords = new ArrayList<>();
        Set<String> uniqueSetWords = new HashSet<>();
        for (LemmaEntity lemmaEntity : lemmas) {
            for (String word : words) {
                CommonWord commonWord = new CommonWord();
                commonWord.setWord(word);
                commonWord.setLength(word.length());
                List<String> wordBaseForms = lemmaConverter.returnWordIntoBaseForm(word);
                if (!wordBaseForms.isEmpty()) {
                    String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                    if (lemmaEntity.getLemma().equals(resultWordForm) && !uniqueSetWords.contains(word)) {
                        uniqueSetWords.add(word);
                        commonWords.add(commonWord);
                        snippetSketch = findFragmentsWithQueryWord(snippet, word, content.toLowerCase()).toString();
                    }
                }
            }
        }
        return checkOnSnippetLength(snippetSketch, commonWords, content, lemmas.size());
    }

    private StringBuilder findFragmentsWithQueryWord(StringBuilder snippet, String word, String content) {
        if (!snippet.toString().contains(word)) {
            Pattern pattern = Pattern.compile("\\b.{0,20}" + "[^a-zа-я]" + word + "[^a-zа-я]" + ".{0,70}\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String fragmentText = content.substring(matcher.start(), matcher.end());
                String text = "..." + fragmentText;
                snippet.append(text);
            }
        }
        return snippet;
    }

    private List<CommonWord> sortListToWordIndex(List<CommonWord> commonWords, String snippet) {
        List<CommonWord> commonWordList = new ArrayList<>();
        for(CommonWord commonWord : commonWords) {
            String regex = "[^a-zа-я]" + commonWord.getWord() + "[^a-zа-я]";
            if(Pattern.compile(regex).matcher(snippet).find()) {
                commonWord.setIndex(snippet.indexOf(commonWord.getWord()));
                commonWordList.add(commonWord);
            }
        }
        Comparator<CommonWord> commonWordComparator = Comparator.comparingInt(CommonWord::getIndex);
        return commonWordList.stream().sorted(commonWordComparator).collect(Collectors.toList());
    }

    private String checkOnSnippetLength(String snippet, List<CommonWord> commonWords, String content, int querySize) {
        List<CommonWord> sortedList = sortListToWordIndex(commonWords, snippet);
        int snippetHalf = checkSnippetSizeForCorrectDivision(snippet, sortedList);
        String firstSnippetHalf = snippet.substring(0, snippetHalf);
        String secondSnippetHalf = snippet.substring(snippetHalf);
        List<String> wordsInFirstHalf = new ArrayList<>();
        List<CommonWord> wordsInSecondHalf = new ArrayList<>(sortedList);

        filterCommonWords(wordsInFirstHalf, wordsInSecondHalf, sortedList, firstSnippetHalf);

        String totalSnippet = snippet;
        if(querySize > 1) {
            String finalFirstHalfVersion = firstSnippetHalf;
            if (firstSnippetHalf.length() > 140) {
                finalFirstHalfVersion = editFirstSnippetHalf(wordsInFirstHalf, firstSnippetHalf);
            }

            String finalSecondHalfVersion = secondSnippetHalf;
            if (secondSnippetHalf.length() > 140) {
                finalSecondHalfVersion = "";
                if (!wordsInSecondHalf.isEmpty()) {
                    finalSecondHalfVersion = "...".concat(editSecondSnippetHalf(wordsInSecondHalf, secondSnippetHalf));
                }
            }
            totalSnippet = finalFirstHalfVersion.concat(finalSecondHalfVersion);
        }
        return addSnippetSizeToLimit(totalSnippet, sortedList, content);
    }

    private int checkSnippetSizeForCorrectDivision(String snippet, List<CommonWord> sortedList) {
        int snippetHalf = snippet.length() / 2;
        int wordIndex = 0;
        String word = "";

        for(CommonWord commonWord : sortedList) {
            if(commonWord.getIndex() > snippetHalf) continue;

            if(commonWord.getIndex() > wordIndex) {
                wordIndex = commonWord.getIndex();
                word = commonWord.getWord();
            }
        }

        boolean isWordInSnippetFirstHalf = snippet.substring(wordIndex, snippetHalf).contains(word);
        return isWordInSnippetFirstHalf ? snippetHalf : snippetHalf - (snippetHalf - wordIndex);
    }

    private void filterCommonWords(List<String> wordsInFirstHalf, List<CommonWord> wordsInSecondHalf,
                                   List<CommonWord> sortedList, String firstHalf) {
        List<String> lemmaWords = new ArrayList<>();
        for (CommonWord commonWord : sortedList) {
            String word = commonWord.getWord();
            List<String> wordBaseForms = lemmaConverter.returnWordIntoBaseForm(word);
            if (!wordBaseForms.isEmpty()) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                if (firstHalf.contains(word)) {
                    if (!lemmaWords.contains(resultWordForm)) lemmaWords.add(resultWordForm);
                    wordsInSecondHalf.remove(commonWord);
                    wordsInFirstHalf.add(word);

                } else {
                    if (lemmaWords.stream().anyMatch(w -> w.equals(resultWordForm)))
                        wordsInSecondHalf.remove(commonWord);
                }
            }
        }
    }

    private String editFirstSnippetHalf(List<String> wordsInFirstHalf, String firstSnippetHalf) {
        StringBuilder snippetFragments = new StringBuilder();
        for (int i = 0; i < wordsInFirstHalf.size(); i++) {
            int firstWordIndex = firstSnippetHalf.indexOf(wordsInFirstHalf.get(i));
            int secondWordIndex;

            int lastIndexOfWord = firstWordIndex + wordsInFirstHalf.get(i).length();
            if (i != wordsInFirstHalf.size() - 1) {
                secondWordIndex = firstSnippetHalf.indexOf(wordsInFirstHalf.get(i + 1));
            } else {
                int indexOfLastDots = firstSnippetHalf.substring(0, firstWordIndex).lastIndexOf("...");
                int indexOfLastSpace = firstSnippetHalf.substring(indexOfLastDots, firstWordIndex - 1).lastIndexOf(" ");
                int indexOfOneWordBeforeQueryWord = indexOfLastDots + indexOfLastSpace;

                snippetFragments.append(firstSnippetHalf, indexOfOneWordBeforeQueryWord, lastIndexOfWord);
                break;
            }

            int diffBetweenFirstAndSecondWord = secondWordIndex - lastIndexOfWord;
            if (diffBetweenFirstAndSecondWord <= 10) {
                snippetFragments.append(firstSnippetHalf, firstWordIndex, secondWordIndex);
            } else {
                snippetFragments.append(firstSnippetHalf, firstWordIndex, secondWordIndex + wordsInFirstHalf.get(i + 1).length());
                int newIndex = snippetFragments.indexOf(wordsInFirstHalf.get(i));
                int lastNewIndex = newIndex + wordsInFirstHalf.get(i).length();
                int newSecondIndex = snippetFragments.indexOf(wordsInFirstHalf.get(i + 1)) + wordsInFirstHalf.get(i + 1).length();
                snippetFragments.replace(lastNewIndex + (diffBetweenFirstAndSecondWord / 2), newSecondIndex, "...");
            }
        }
        int indexFirstWord = firstSnippetHalf.indexOf(wordsInFirstHalf.stream().findFirst().orElseThrow());
        return firstSnippetHalf.substring(0, indexFirstWord).concat(snippetFragments.toString());
    }

    private String editSecondSnippetHalf(List<CommonWord> wordsInSecondHalf, String secondSnippetHalf)
    {
        StringBuilder snippetFragments = new StringBuilder();
        for (int i = 0; i < wordsInSecondHalf.size(); i++) {
            int firstWordIndex = secondSnippetHalf.indexOf(wordsInSecondHalf.get(i).getWord());
            int secondWordIndex;

            int lastIndexOfWord = firstWordIndex + wordsInSecondHalf.get(i).getWord().length();
            if (i != wordsInSecondHalf.size() - 1) {
                secondWordIndex = secondSnippetHalf.indexOf(wordsInSecondHalf.get(i + 1).getWord());
            } else {
                int indexOfLastDots = secondSnippetHalf.substring(0, firstWordIndex).lastIndexOf("...");
                int indexOfLastSpace = secondSnippetHalf.substring(indexOfLastDots, firstWordIndex - 1).lastIndexOf(" ");
                int start = indexOfLastDots + indexOfLastSpace;

                String oneWordBeforeQueryWord = secondSnippetHalf.substring(start, firstWordIndex).trim();
                int indexOfOneWordBeforeQueryWord = secondSnippetHalf.indexOf(oneWordBeforeQueryWord);
                snippetFragments.append(secondSnippetHalf, indexOfOneWordBeforeQueryWord, lastIndexOfWord);
                break;
            }

            int diffBetweenFirstAndSecondWord = secondWordIndex - lastIndexOfWord;
            if (diffBetweenFirstAndSecondWord <= 10) {
                snippetFragments.append(secondSnippetHalf, firstWordIndex, secondWordIndex);
            } else {
                snippetFragments.append(secondSnippetHalf, firstWordIndex, secondWordIndex + wordsInSecondHalf.get(i + 1).getWord().length());
                int newIndex = snippetFragments.indexOf(wordsInSecondHalf.get(i).getWord());
                int lastNewIndex = newIndex + wordsInSecondHalf.get(i).getWord().length();
                int newSecondIndex = snippetFragments.indexOf(wordsInSecondHalf.get(i + 1).getWord()) + wordsInSecondHalf.get(i + 1).getWord().length();
                snippetFragments.replace(lastNewIndex + (diffBetweenFirstAndSecondWord / 2), newSecondIndex, "...");
            }
        }

        return snippetFragments.toString();
    }

    private String addSnippetSizeToLimit(String snippet, List<CommonWord> sortedList, String content) {
        content = content.toLowerCase();
        StringBuilder builder = new StringBuilder(snippet);
        if(snippet.length() <= SNIPPET_MAX_SIZE) {
            int lastDots = snippet.lastIndexOf("...");
            String lastFragment = snippet.substring(lastDots + 3);
            int lastSnippetIndexInContent = content.lastIndexOf(lastFragment);
            int extraIndexAmount = (lastSnippetIndexInContent  +
                    lastFragment.length()) + (SNIPPET_MAX_SIZE - snippet.length());
            String finalVersion = content.substring(lastSnippetIndexInContent +
                    lastFragment.length(), extraIndexAmount);
            snippet = builder.append(finalVersion).toString();
        }

        if (snippet.length() > SNIPPET_MAX_SIZE) {
            snippet = snippet.substring(0, SNIPPET_MAX_SIZE);
        }

        return addTagsToWordInSnippet(snippet, sortedList);
    }

    private String addTagsToWordInSnippet(String snippet, List<CommonWord> commonWords) {
        String finalSnippet = snippet;
        Comparator<CommonWord> commonWordComparator = Comparator.comparingInt(CommonWord::getLength);
        Set<CommonWord> finalCommonWordSet = commonWords.stream().sorted(commonWordComparator.reversed()).collect(Collectors.toCollection(LinkedHashSet::new));

        for(CommonWord commonWord : finalCommonWordSet) {
            String word = commonWord.getWord();
            String regex = "[^a-zа-я]" + commonWord.getWord() + "[^a-zа-я]";
            finalSnippet = finalSnippet.replaceAll(regex, " <b>" + word + "</b> ");
        }

        return finalSnippet.concat("...");
    }
}

package searchengine.proccessing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import searchengine.dto.search.CommonWord;
import searchengine.lemma.LemmaConverter;
import searchengine.model.LemmaEntity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SnippetCreation {
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
                        commonWord.setLemma(resultWordForm);
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
                if (!snippet.toString().contains(fragmentText)) snippet.append(text);
            }
        }
        return snippet;
    }

    private String checkOnSnippetLength(String snippet, List<CommonWord> commonWords, String content, int querySize) {
        List<CommonWord> sortedList = sortListToWordIndex(commonWords, snippet);
        int snippetHalf = checkSnippetSizeForCorrectDivision(snippet, sortedList);
        String firstSnippetHalf = snippet.substring(0, snippetHalf);
        String secondSnippetHalf = snippet.substring(snippetHalf);
        List<CommonWord> wordsInFirstHalf = new ArrayList<>();
        List<CommonWord> wordsInSecondHalf = new ArrayList<>();
        List<String> filteredWordList = new ArrayList<>();

        filterCommonWordsInHalf(wordsInFirstHalf, sortedList, firstSnippetHalf, filteredWordList);
        filterCommonWordsInHalf(wordsInSecondHalf, sortedList, secondSnippetHalf, filteredWordList);

        String totalSnippet = snippet;
        if (querySize > 1) {
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

    private void filterCommonWordsInHalf(List<CommonWord> wordsInHalf,
                                         List<CommonWord> sortedList,
                                         String snippetHalf,
                                         List<String> filteredWordList) {
        for (CommonWord commonWord : sortedList) {
            String word = commonWord.getWord();
            String lemma = commonWord.getLemma();
            String regex = "[^a-zа-я]" + word + "[^a-zа-я]";
            Matcher matcher = Pattern.compile(regex).matcher(snippetHalf);
            CommonWord cw = new CommonWord();
            cw.setWord(word);
            cw.setLemma(lemma);
            if (matcher.find() && !filteredWordList.contains(lemma)) {
                int index = snippetHalf.substring(matcher.start(), matcher.end()).indexOf(word);
                cw.setIndex(matcher.start() + index);
                cw.setLength(word.length());
                wordsInHalf.add(cw);
                filteredWordList.add(lemma);
            }
        }
    }

    private List<CommonWord> sortListToWordIndex(List<CommonWord> commonWords, String snippet) {
        List<CommonWord> commonWordList = new ArrayList<>();
        for (CommonWord commonWord : commonWords) {
            String regex = "[^a-zа-я]" + commonWord.getWord() + "[^a-zа-я]";
            Matcher matcher = Pattern.compile(regex).matcher(snippet);
            if (matcher.find()) {
                commonWord.setIndex(matcher.start());
                commonWordList.add(commonWord);
            }
        }
        Comparator<CommonWord> commonWordComparator = Comparator.comparingInt(CommonWord::getIndex);
        return commonWordList.stream().sorted(commonWordComparator).collect(Collectors.toList());
    }

    private int checkSnippetSizeForCorrectDivision(String snippet, List<CommonWord> sortedList) {
        int snippetHalf = snippet.length() / 2;
        int wordIndex = 0;
        String word = "";

        for (CommonWord commonWord : sortedList) {
            if (commonWord.getIndex() > snippetHalf) continue;

            if (commonWord.getIndex() > wordIndex) {
                wordIndex = commonWord.getIndex();
                word = commonWord.getWord();
            }
        }

        boolean isWordInSnippetFirstHalf = snippet.substring(wordIndex, snippetHalf).contains(word);
        return isWordInSnippetFirstHalf ? snippetHalf : snippetHalf - (snippetHalf - wordIndex);
    }

    private String editFirstSnippetHalf(List<CommonWord> wordsInFirstHalf, String firstSnippetHalf) {
        StringBuilder snippetFragments = new StringBuilder();
        for (int i = 0; i < wordsInFirstHalf.size(); i++) {
            int firstWordIndex = wordsInFirstHalf.get(i).getIndex();
            int lastIndexOfWord = firstWordIndex + wordsInFirstHalf.get(i).getLength();
            int secondWordIndex;
            if (i != wordsInFirstHalf.size() - 1) {
                secondWordIndex = wordsInFirstHalf.get(i + 1).getIndex();
            } else {
                int indexOfLastSpace = firstSnippetHalf.substring(0, firstWordIndex).lastIndexOf(" ");
                if(indexOfLastSpace == -1) {
                    indexOfLastSpace = firstWordIndex - 1;
                }
                snippetFragments.append(firstSnippetHalf, indexOfLastSpace + 1, lastIndexOfWord);
                break;
            }
            int diffBetweenFirstAndSecondWord = secondWordIndex - lastIndexOfWord;
            if (diffBetweenFirstAndSecondWord <= 15) {
                snippetFragments.append(firstSnippetHalf, firstWordIndex, secondWordIndex);
                snippetFragments.insert(snippetFragments.indexOf(wordsInFirstHalf.get(i).getWord())  + wordsInFirstHalf.get(i).getLength(), " ");
            } else {
                int firstWordLen = wordsInFirstHalf.get(i).getLength();
                int secondWordLen = wordsInFirstHalf.get(i + 1).getLength();
                int division = diffBetweenFirstAndSecondWord / 2;

                String edit = firstSnippetHalf.substring(firstWordIndex, secondWordIndex + secondWordLen);
                int start = edit.indexOf(wordsInFirstHalf.get(i).getWord()) + firstWordLen + division;
                int end = edit.lastIndexOf(wordsInFirstHalf.get(i + 1).getWord()) + secondWordLen;

                String checkForCorrectDivision = edit.substring(0, start + 1);
                int lastSpace = checkForCorrectDivision.lastIndexOf(" ");
                if(lastSpace == -1) {
                    lastSpace = edit.indexOf(wordsInFirstHalf.get(i).getWord()) + firstWordLen;
                }
                String fix = new StringBuilder(edit).replace(lastSpace, end, "").toString();
                if (fix.endsWith(" ")) fix = fix.substring(0, fix.length() - 1);
                snippetFragments.append(fix).append("... ");
            }
        }

        int indexFirstWord = wordsInFirstHalf.stream().findFirst().orElseThrow().getIndex();
        return firstSnippetHalf.substring(0, indexFirstWord).concat(snippetFragments.toString());
    }

    private String editSecondSnippetHalf(List<CommonWord> wordsInSecondHalf, String secondSnippetHalf) {
        StringBuilder snippetFragments = new StringBuilder();
        for (int i = 0; i < wordsInSecondHalf.size(); i++) {
            int firstWordIndex = wordsInSecondHalf.get(i).getIndex();
            int lastIndexOfWord = firstWordIndex + wordsInSecondHalf.get(i).getLength();
            int secondWordIndex;
            if (i != wordsInSecondHalf.size() - 1) {
                secondWordIndex = wordsInSecondHalf.get(i + 1).getIndex();
            } else {
                int indexOfLastSpace = secondSnippetHalf.substring(0, firstWordIndex).lastIndexOf(" ");
                if(indexOfLastSpace == -1) {
                    indexOfLastSpace = firstWordIndex - 1;
                }
                snippetFragments.append(secondSnippetHalf, indexOfLastSpace + 1, lastIndexOfWord);
                break;
            }

            int diffBetweenFirstAndSecondWord = secondWordIndex - lastIndexOfWord;
            if (diffBetweenFirstAndSecondWord <= 15) {
                snippetFragments.append(secondSnippetHalf, firstWordIndex, secondWordIndex);
                snippetFragments.insert(snippetFragments.indexOf(wordsInSecondHalf.get(i).getWord()) + wordsInSecondHalf.get(i).getLength() , " ");
            } else {
                int firstWordLen = wordsInSecondHalf.get(i).getLength();
                int secondWordLen = wordsInSecondHalf.get(i + 1).getLength();
                int division = diffBetweenFirstAndSecondWord / 2;
                String edit = secondSnippetHalf.substring(firstWordIndex, secondWordIndex + secondWordLen);
                int start = edit.indexOf(wordsInSecondHalf.get(i).getWord()) + firstWordLen + division;
                int end = edit.lastIndexOf(wordsInSecondHalf.get(i + 1).getWord()) + secondWordLen;

                String checkForCorrectDivision = edit.substring(0, start + 1);
                int lastSpace = checkForCorrectDivision.lastIndexOf(" ");
                if(lastSpace == -1) {
                    lastSpace = edit.indexOf(wordsInSecondHalf.get(i).getWord()) + firstWordLen;
                }
                String fix = new StringBuilder(edit).replace(lastSpace, end, "").toString();
                if (fix.endsWith(" ")) fix = fix.substring(0, fix.length() - 1);
                snippetFragments.append(fix).append("... ");
            }
        }

        return snippetFragments.toString();
    }

    private String addSnippetSizeToLimit(String snippet, List<CommonWord> sortedList, String content) {
        content = content.toLowerCase();
        StringBuilder builder = new StringBuilder(snippet);
        if (snippet.length() <= SNIPPET_MAX_SIZE && !snippet.isEmpty()) {
            int lastDots = snippet.lastIndexOf("...");
            String lastFragment = snippet.substring(lastDots + 3);
            int lastSnippetIndexInContent = content.lastIndexOf(lastFragment);
            int fragmentStartPoint = lastSnippetIndexInContent + lastFragment.length();
            int fragmentEndPoint = fragmentStartPoint + (SNIPPET_MAX_SIZE - snippet.length());
            if (fragmentEndPoint > content.length()) {
                snippet = getMoreWordsToAchieveLimitSize(builder, content, fragmentStartPoint);
            } else {
                String finalVersion = content.substring(fragmentStartPoint, fragmentEndPoint);
                snippet = builder.append(" ").append(finalVersion).toString();
            }
        }

        if (snippet.length() > SNIPPET_MAX_SIZE) {
            snippet = snippet.substring(0, SNIPPET_MAX_SIZE);
        }

        return addTagsToWordInSnippet(snippet, sortedList);
    }

    private String getMoreWordsToAchieveLimitSize(StringBuilder builder, String content, int start) {
        int availableChars = content.length() - start;
        int extra = (SNIPPET_MAX_SIZE - builder.length()) - availableChars;

        String finalVersion = content.substring(start, start + availableChars);
        builder.append(finalVersion).append("...");

        String remainder = content.substring(0, extra);
        builder.append(remainder);
        return builder.toString();
    }

    private String addTagsToWordInSnippet(String snippet, List<CommonWord> commonWords) {
        String finalSnippet = snippet;

        if(!finalSnippet.isEmpty()) {
            Comparator<CommonWord> commonWordComparator = Comparator.comparingInt(CommonWord::getLength);
            Set<CommonWord> finalCommonWordSet = commonWords.stream().sorted(commonWordComparator.reversed()).collect(Collectors.toCollection(LinkedHashSet::new));
            for (CommonWord commonWord : finalCommonWordSet) {
                String word = commonWord.getWord();
                String regex = "[^a-zа-я]" + commonWord.getWord() + "[^a-zа-я]";
                finalSnippet = finalSnippet.replaceAll(regex, " <b>" + word + "</b> ");
            }

            int space = finalSnippet.lastIndexOf(" ");
            finalSnippet = finalSnippet.substring(0, space);
        }
        return finalSnippet.endsWith("...") ? finalSnippet : finalSnippet.concat("...");
    }
}

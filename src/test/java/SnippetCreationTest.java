import junit.framework.TestCase;
import searchengine.dto.search.CommonWord;

import java.util.ArrayList;
import java.util.List;

public class SnippetCreationTest extends TestCase
{
    String firstSnippetHalf =
            "... удалённо. «быстрый старт на маркетплейсах» — понять," +
            " как выбрать маркетплейс и товар," +
            " который .... поэтому на старте лучше использовать рекламу." +
                    " как работает реклама на wildberries ..." +
            "свой бизнес на маркетплейсе с нуля изучаем wildberries от а до я." +
                    " как выбирать товар и оформлять ... «основы";
    List<CommonWord> wordsInFirstHalf = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        CommonWord commonWord1 = new CommonWord();
        commonWord1.setWord("старт");
        commonWord1.setLength(5);
        commonWord1.setIndex(23);
        CommonWord commonWord2 = new CommonWord();
        commonWord2.setWord("маркетплейсах");
        commonWord2.setLength(13);
        commonWord2.setIndex(32);
        CommonWord commonWord3 = new CommonWord();
        commonWord3.setWord("маркетплейс");
        commonWord3.setLength(11);
        commonWord3.setIndex(69);
        CommonWord commonWord4 = new CommonWord();
        commonWord4.setWord("старте");
        commonWord4.setLength(6);
        commonWord4.setIndex(114);
        CommonWord commonWord5 = new CommonWord();
        commonWord5.setWord("маркетплейсе");
        commonWord5.setLength(12);
        commonWord5.setIndex(204);
        wordsInFirstHalf.add(commonWord1);
        wordsInFirstHalf.add(commonWord2);
        wordsInFirstHalf.add(commonWord3);
        wordsInFirstHalf.add(commonWord4);
        wordsInFirstHalf.add(commonWord5);
        super.setUp();
    }

    public void testEditFirstSnippetHalf() {
        StringBuilder snippetFragments = new StringBuilder();
        for (int i = 0; i < wordsInFirstHalf.size(); i++) {
            int firstWordIndex = wordsInFirstHalf.get(i).getIndex();
            int lastIndexOfWord = firstWordIndex + wordsInFirstHalf.get(i).getLength();
            int secondWordIndex;
            if (i != wordsInFirstHalf.size() - 1) {
                secondWordIndex = wordsInFirstHalf.get(i + 1).getIndex();
            } else {
                int indexOfLastDots = firstSnippetHalf.substring(0, firstWordIndex).lastIndexOf("...");
                int indexOfLastSpace = firstSnippetHalf.substring(indexOfLastDots, firstWordIndex).lastIndexOf(" ");
                int indexOfOneWordBeforeQueryWord = indexOfLastDots + indexOfLastSpace;
                snippetFragments.append(firstSnippetHalf, indexOfOneWordBeforeQueryWord + 1, lastIndexOfWord);
                break;
            }
            int diffBetweenFirstAndSecondWord = secondWordIndex - lastIndexOfWord;
            if (diffBetweenFirstAndSecondWord <= 10) {
                snippetFragments.append(firstSnippetHalf, firstWordIndex, secondWordIndex);
            } else {
                int firstWordLen = wordsInFirstHalf.get(i).getLength();
                int secondWordLen = wordsInFirstHalf.get(i + 1).getLength();
                int division = diffBetweenFirstAndSecondWord / 2;

                String edit = firstSnippetHalf.substring(firstWordIndex, secondWordIndex + secondWordLen);
                int start = edit.indexOf(wordsInFirstHalf.get(i).getWord()) + firstWordLen + division;
                int end = edit.lastIndexOf(wordsInFirstHalf.get(i + 1).getWord()) + secondWordLen;
                System.out.println();
                String fix = new StringBuilder(edit).replace(start, end, "").toString();
                if (fix.endsWith(" ")) fix = fix.substring(0, fix.length() - 1);
                snippetFragments.append(fix).append("...");
            }
        }

        int indexFirstWord = wordsInFirstHalf.stream().findFirst().orElseThrow().getIndex();
        var result = firstSnippetHalf.substring(0, indexFirstWord).concat(snippetFragments.toString());
        System.out.println();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}

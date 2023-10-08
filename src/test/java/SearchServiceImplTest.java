import junit.framework.TestCase;
import searchengine.lemma.LemmaConverter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SearchServiceImplTest extends TestCase
{
    String snippet;
    String query = "Функции зарядки";
    Set<String> commonWords;

    // инициализация данных
    @Override
    protected void setUp() throws Exception {
        snippet = "...частота обновления экрана 120 гц мультимедийные возможности функции камеры автофокусировка, оптическая стабилизация, основная камера, тыловая вспышка, фронтальная камера, hdr, автофокус, двойная вспышка, ночной режим, портретный режим, сверхширокоугольный объектив, .... купить автомобильный держатель с беспроводной быстрой зарядкой baseus rock-solid electric (wxhw01-b0s) (артикул: 112436) 2800р. купить автомобильный держатель borofone bh37 (артикул: 112746) 800р. купить автомобильный держатель hoco ca40 черный (артикул: 112747... все памятные моменты в потрясающем качества. турбо зарядка мощностью 67 вт за считанные минуты способна зарядить батарею на несколько часов работы. флагманская производительность 4-нм процессор snapdragon 8+ gen1 с центральным и графическим процессорами ... poco f5 pro поддерживает супер-быструю беспроводную зарядку мощностью 30 вт, а это значит, что вы можете забыть о проводах и сложных подключениях. увеличение срока службы батареи технология интеллектуальной зарядки учитывает ваш опыт зарядок и заботится о";
        commonWords = new HashSet<>(6);
        commonWords.add("зарядок");
        commonWords.add("зарядки");
        commonWords.add("зарядка");
        commonWords.add("функции");
        commonWords.add("зарядкой");
        commonWords.add("зарядку");

        super.setUp();
    }

    public void testCheckOnSnippetLength() {
//        StringBuilder builder = new StringBuilder();
//        String[] words = LemmaConverter.splitContentIntoWords(query);
//        for (String commonWord : commonWords) {
//            snippet = snippet.replaceAll(commonWord, "<b>" + commonWord + "</b>");
//        }
//
//        if(snippet.length() > 260 && words.length > 1) {
//            String firstWord = Arrays.stream(words).findFirst().get();
//            int i = snippet.indexOf(firstWord);
//            String content = snippet.substring(0, i + firstWord.length() + 4);
//            for(String word : Arrays.stream(words).skip(1).collect(Collectors.toSet())) {
//                content = content.concat("..." + snippet.substring(snippet.indexOf(word) - 3));
//            }
//            snippet = content;
//        }
//
//        var size = snippet.length();
//        System.out.println();
//        assertTrue(snippet.length() >= 260);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}

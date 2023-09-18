import junit.framework.TestCase;
import lombok.extern.slf4j.Slf4j;
import searchengine.lemma.LemmaConverter;

import java.util.HashMap;
import java.util.List;

@Slf4j
public class LemmaConverterTest extends TestCase
{
    String content;
    String [] words;
    HashMap<String, String> wordLemmaMap;

    // инициализация данных
    @Override
    protected void setUp() throws Exception {
        content = "Ревизор.Версия <img height=\"1\" width=\"1\" src=\"https://www.facebook.com/tr?id=442473676182880&amp;ev=PageView &amp;noscript=1\"> <img height=\"1\" width=\"1\" style=\"display:none\" src=\"https://www.facebook.com/tr?id=990104738185375&amp;ev=PageView&amp;noscript=1\"> <img height=\"1\" width=\"1\" style=\"display:none\" src=\"https://www.facebook.com/tr?id=1547161622006603&amp;ev=PageView&amp;noscript=1\"> <img height=\"1\" width=\"1\" style=\"display:none\" src=\"https://www.facebook.com/tr?id=323731701614932&amp;ev=PageView&amp;noscript=1\"> <img height=\"1\" width=\"1\" style=\"display:none\" src=\"https://www.facebook.com/tr?id=306357120857848&amp;ev=PageView&amp;noscript=1\"> <img height=\"1\" width=\"1\" src=\"https://www.facebook.com/tr?id=166074288854711&amp;ev=PageView&amp;noscript=1\"> <div><img src=\"https://mc.yandex.ru/watch/26781675\" style=\"position:absolute; left:-9999px;\" alt=\"\"> <img src=\"https://vk.com/rtrg?p=VK-RTRG-926297-hXPUb\" style=\"position:fixed; left:-999px;\" alt=\"\"> Подписка на новости Поиск по сайту Версия для слабовидящих Заказ билетов: +7 (495) 781 781 1 Пушкинская карта Премия зрительских симпатий АРТИСТ ГОДА МОСКОВСКИЙ ТЕАТР «Et Cetera» художественный руководитель александр калягин главный режиссер Роберт Стуруа афиша театр спектакли Детские спектакли билеты труппа новости Новости mos.ru пресса гастроли контакты Отзывы Промо ролик Ближайшие спектакли: 20 сентября 19:0030 сентября 19:0017 октября 19:0029 октября 18:00 Ревизор.Версия Автор: Н.В.Гоголь Жанр: комедия 16+ Еще одна версия из множества других: выдающихся постановок и канувших в Лету, сохранившихся в истории и бесследно исчезнувших, поставленных в России и за рубежом. Какова же версия Стуруа? Анекдот, парадоксальные размышления о современном абсурде или русская комедия масок? Наверное, и то, и другое, и третье. Но самое важное, как написал Андрей Максимов в Российской газете: «…остался Гоголь. Зал смеется и аплодирует репликам, написанным почти 200 лет назад... Великий текст звучит не хрестоматийно, а живо». Александр Калягин стал лауреатом театральной премии \"Московский комсомолец\" (сезон 2016/2017) в категории \"Мэтры: лучшая мужская роль\", а также лауреатом театральной премии \"Звезда Театрала\" в номинации \"Лучшая мужская роль\" - за исполнение роли Хлестакова в спектакле \"Ревизор.Версия\". Спектакль - лауреат театральной премии СТД РФ \"Гвоздь сезона\" (2018). Роберт Стуруа стал лауреатом премии \"Биеннале театрального искусства\" 2018 - \"за урок соединения отваги решения и воплощения\" (спектакль \"Ревизор.Версия\"). За исполнение главных ролей в спектакле «Ревизор.Версия» народная артистка РФ Наталья Благих и заслуженный артист РФ Владимир Скворцов были удостоены Премии города Москвы в области литературы и искусства (сентябрь 2020г.). При поддержке Русской медной компании Продолжительность спектакля 1 час 30 минут без антракта Премьера состоялась 16 мая 2017 года Режиссер народный артист СССР Роберт Стуруа Идея художественного оформления Александр Боровский Художник по костюмам Анна Нинуа Ассистент режиссера Елена Лукьянчикова Художник по свету Андрей Абрамов Художники по гриму Мария Максимова заслуженный деятель искусств РФ Николай Максимов Педагог по вокалу Ирина Мусаелян Хореография Константин Мишин Педагог по речи Валерия Устинова Звукорежиссеры Иван Краснов Артем Моргунов Помощники режиссера Ольга Моисеева Любовь Дмитриева Суфлер Татьяна Горячева Действующие лица и исполнители: Антон Антонович Сквозник-Дмухановский, городничий заслуженный артист РФ Владимир Скворцов Анна Андреевна, жена его народная артистка РФ Наталья Благих Марья Антоновна, дочь его Кристина Гагуа Лука Лукич Хлопов, смотритель училищ Иван Косичкин Аммос Фёдорович Ляпкин-Тяпкин, судья заслуженный артист РФ Сергей Тонгур Артемий Филиппович Земляника, попечитель богоугодных заведений Артём Блинов Иван Кузьмич Шпекин, почтмейстер Евгений Шевченко Пётр Иванович Добчинский, городской помещик Андрей Кондаков Евгений Тихомиров Пётр Иванович Бобчинский, городской помещик Федор Бавтриков Евгений Тихомиров Купцы Александр Жоголь Евгений Тихомиров Иван Александрович Хлестаков,чиновник из Петербурга народный артист РСФСР Александр Калягин Осип, его слуга Григорий Старостин Христиан Иванович Гибнер Тимофей Дунаев Авдотья Елизавета Рыжих Хозяин трактира Кирилл Лоскутов Пресса: Версия Стуруа (Экран и сцена, 01.02.2021) Над кем смеетесь? Как в театрах показывают \"Ревизора\" (РИА Новости \"Культура\", 13.11.2019) В Германии с оглушительным успехом завершились гастроли театра Et Cetera (Вести, 15.10.2019) В Берлине состоялись гастроли театра Et Cetera (Иван Благой, 14.10.2019) Артисты театра Et Cetera покажут в Берлине спектакль \"Ревизор. Версия\" (Валерия Кудрявцева, Телеканал \"Культура\", 11.10.2019) Александр Калягин обвел вокруг пальца чиновников города N (Анастасия Плешакова, Комсомольская правда, 25.09.2019) Гоголь вернулся в Италию (Александр Калягин, Журнал \"Театрал\", 30.10.2018) Гоголь и «Ревизор» московской школы в Театре Аргентина (Родольфо ди Джаммарко, La Repubblica di Roma («Римская республика»), 17.09.2018) Версия \"Ревизора\" Гоголя на сцене театра \"Аргентина\" (Риккардо Ченчи, Eurocomunicazione, 17.09.2018) Приехал ревизор. Он стар, обездвижен и сидит в инвалидной коляске. (Энрико Фьоре, CONTROSCENA.NET, 16.09.2018) В Рим приехал \"Ревизор\"! (Нива Миракян (Рим) , Российская газета - Федеральный выпуск №7670 (207), 16.09.2018) Версия с Александром Калягиным и актерами труппы Московского театра «Et Cetera» (Марикла Боджо, Criticateatrale.net, 14.09.2018) Театр Аргентина. Александр Калягин в спектакле «Ревизор. Версия». (Ди Франческо, 09.09.2018) Кто в замке король? В театре у Александра Калягина (Татьяна Москвина, Аргументы Недели, 12.07.2018) Лабардан по-московски (Мария Кингисепп, Вечерний Санкт-Петербург, 23.05.2018) Александр Калягин показал петербургским зрителям «Ревизора» (Культурная эволюция, 22.05.2018) Некто странной наружности (Елена Омеличкина, 01.05.2018) Гастроли театра «Et Сetera» произвели фурор в Тбилиси (Римма Берулава, Первый канал, 04.04.2018) Московский \"Ревизор\" в постановке Роберта Стуруа потряс грузинских зрителей (Ольга Свистунова , ТАСС, 06.04.2018) Это — «Гвоздь сезона»! (Первый канал. Доброе утро. , 06.03.2018) Александр Калягин - лауреат премии \"Звезда Театрала\" (Театрал, 05.12.2017) Страшный Суд был вчера (Наталия Каминская, журнал \"Сцена\" №4, 01.09.2017) Старик и горе (Ирина Удянская, WATCH, 20.08.2017) К нам приехал \"Ревизор.Версия\": Александр Калягин предстал в образе инфернального Хлестакова (Слава Шадронов, Окно в Москву, 16.08.2017) Ревизор приходит дважды (Елизавета Авдошина, Независимая газета, 21.06.2017) Хлесткий Хлестаков (Андрей Максимов, Российская газета, 18.06.2017) Александр Калягин: Все знать, читать и сделать по-своему (Анжелика Заозерская, Вечерняя Москва, 14.06.2017) Александр Калягин прикинулся Ревизором (Анастасия Плешакова, Комсомольская правда, 07.06.2017) Последний день города N: Как пьесу Гоголя «Ревизор» превратили в «Карточный домик» (Анна Гордеева, Lenta.ru, 06.06.2017) «А рыба была хороша!» (Марина Токарева, Новая газета, 05.06.2017) Александр Калягин побил рекорды, сыграв в 75 лет молодого Хлестакова (Марина Райкина, Московский комсомолец, 01.06.2017) Из «Шинели» вышел ревизор (Денис Сутыка, газета \"Культура\", 31.05.2017) По щучьему велению и именному повелению: Александр Калягин сыграл Хлестакова (Ольга Егошина, Театрал, 31.05.2017) Александр Калягин отметил юбилей ролью Хлестакова в спектакле Стуруа (Телеканал \"Культура\": новости культуры с Владиславом Флярковским, 27.05.2017) «Ревизор. Версия» (Филипп Резников, Rara Avis, 22.05.2017) Александр Калягин готовит к своему 75-летию роль Хлестакова в спектакле \"Ревизор\" (ТАСС, 24.04.2017) Александр Калягин сыграет самого зрелого Хлестакова (Елизавета Авдошина, Независимая газета, 23.04.2017) Александр Калягин сыграет Хлестакова (Марина Райкина, Московский комсомолец, 29.08.2016) Что такое талант? Можно об этом говорить, а можно увидеть, понять и ощутить его проявление. На постановку \"Ревизор\" в EtCetera, наверное, как и многие другие зрители, мы шли ради одного - посмотреть игру Мастера. Калягин был великолепен! Тот образ, который он сумел создать, уникален и даже рушит стереотипы о известном нам персонаже Гоголя Хлестакове. Герой Калягина одновременно близок к классическому прочтению текста и к современным реалиям жизни. Талант Александра Александровича в этом спектакле раскрывается в полной мере! Мастер играет эмоциями, задействуя только мимику и пластику рук. А сколько в этом юмора! Он, оставаясь почти неподвижным, приковывает к себе внимание зала. В своей игре он сродни образам Чарли Чаплинских героев. Как поется: \"Великий маг. Не промолвив даже слова, он все сказал\". Браво! Смотреть! Смотреть! Обязательно смотреть! Там, к слову, прекрасная игра всего актёрского состава. Евгения 22.03.2023 Самое сильное впечатление произвел возраст главного героя. Это было настолько неожиданно, что я полностью изменила свое мнение о пределе человеческой бессовестности. Левитирующая шкатулка и перо-предсказатель, светящиеся окна и выпадающие из них граждане, летающий сюртук и зловещая служанка, которая превращается в обычную женщину под магией любви. Насколько глубока в нас алчность, продажность и порочность режиссер показал мастерски. Все впечатление усиливается с помощью замызганной жилетки, старческой дремоты и лебезящих чиновников. Ничегошеньки не изменилось, все по прежнему. И только финальная сцена внушает надежду на выход из тупика. Это кстати тоже версия, Гоголь задумал немного другую концовку))) Александра 12.05.2022 Не являюсь искушенным театралом, скорее, просто любителем, причем в силу возраста достаточно консервативным. Быть может, поэтому пишу отзыв для таких же простых зрителей, коим являюсь сама. Что ж, \"Ревизор. Версия\" - это, на мой взгляд, шедевр. И, как у большинства шедевров, у него будут те, кто упоён и впечатлён до глубины глубин и те, кто не понял и остался в лёгком недоумении. Перед посещением спектакля читала отзывы, встречались отрицательные. Шла на просмотр, что называется, \"во всеоружии\". По этой причине хочется дать совет тем, кто решил вкусить плодов Мельпомены и Талии: не ищите сходства с произведением. Расслабьтесь, погрузитесь в происходящее на сцене и вкушайте. Это не тот классический \"Ревизор\", это \"Ревизор. Версия\". Версия, удивительно сумевшая передать эмоциональную нить творения Гоголя, где-то усилив идею, где-то убрав несущественное. Спектакль удивителен. Неповторим. Он со вкусом, он продуман, он утончен и глубок, как всё в этом театре, как в личности Мэтра. Спектакль для искушенных. Спектакль для уставших. Спектакль для эмоциональных. Для юных и зрелых. Мэтр бесподобен, впрочем, как всегда. Гений, благодаря которому моя жизнь ярче и насыщеннее. Люблю и обожаю, здоровья и долгие лета. Роберт Стуруа - мой поклон и почитание Гению, искренние пожелания здоровья и творческих воплощений. Театру и труппе процветания. Неповторимое место. Спасибо! Инесса 13.04.2022 Спасибо большое за возможность посетить театр Et Cetera.Была там впервые. Помещение, архитектура, интерьер-все замечательно. Но и необычная постановка старинной пьесы тоже очень понравилась. Если раньше казалось, что это обращение к зрителю прошедших поколений. А сейчас ,мне кажется, это к тем, кто сидит в зрительном зале сейчас-современным городничим и остальным героям. Посмотрите на себя. Ужаснитесь. Ведь ничего не меняется Людмила 21.01.2022 Спектакль прекрасный. Длится полтора часа без антракта. Мистический ужас персонажей передан неожиданными звуковыми, световыми и шумовыми эффектами, изумительной пластикой актеров. Акценты в репликах смещены, часть текста Гоголя опущена. Такое прочтение текста помогает изменить образ Хлестакова, которого прелестно и тонко играет Калягин. Это толстый пожилой, вечно засыпающий человек в инвалидной коляске. Увиваться за дамами он не может и не хочет. Соблазняют его сами дамы и тут есть над чем посмеяться зрителям. Повороты сюжета неожиданы, действие динамично. Все это держит интерес до самого конца спектакля. Мария 21.01.2022 Что если вы придете в театр и увидите, как в комнату городской гостиницы выезжает в кресле-каталке дряхлеющий Хлестаков с проступающими признаками Альцгеймера, регулярно засыпающий на полуслове и в силу старости уже не способный приударить ни за кем: ни за дочкой Городничего, ни за его женой, - отчего сцены коварного соблазнения столичного ревизора выглядят особо комично и неприкрыто цинично? Что если ложный ревизор, собравший со всего города добровольную дань, на самом деле окажется не ложным, а самым что ни на есть настоящим? Как вам такая Версия? «Ревизор. Версия». Театр «Et cetera». Именно на этой сцене несравненный мастер, художественный руководитель театра Александр Калягин, воплотил исключительно неожиданный и яркий образ возможного Хлестакова – такого, каким он мог бы быть, не читай мы «Замечания» Николая Васильевича, и каким бы он мог быть и в авторской интерпретации, если б Гоголь написал свои комментарии к комедии после сожжения второго тома «Мертвых душ» и после болезненной критики его «Выбранных мест из переписки с друзьями». Невероятно слаженный комичный дуэт Александра Калягина с Натальей Благих (Городничиха) вывел известную со школьной скамьи сцену соблазнения на небывалые высоты ироничной эксцентрики – такой Городничихи нигде больше вы не увидите. Дарья Колечкина 10.09.2021 Добрый день, Я бы хотела поблагодарить Ваш прекрасный театр за вчерашнего «Ревизора», это было просто блестяще: - сценография бесподобна и выше всяких похвал, ты забываешь, что это сцена и чувствуешь себя участником событий. - подбор музыки великолепен и опять же, ассоциация с английским театром, это очень по-английски: изящнейшим образом упакованный стёб: гимн США в финальной сцене - это вообще ВАУ и очень смешно - замечательные костюмы - это Наталья Благих, прекрасная не только мастерством перевоплощений, но и ТЕЛОМ, великолепная актриса, настоящая ДИВА и просто оооочень красивая женщина - бесподобная игра актеров, это единый и очень органичный коллектив, все индивидуальности и личности, умеющие существовать на сцене, как единый организм, одно целое. - и низкий поклон Александру Александровичу за нереальную энергетику, которую чувствуешь каждой клеткой. У него был дэрэ на днях, не знаю, сколько лет ему исполнилось ибо его хулиганский задор, гениальность перевоплощений и чувство юмора зашкаливают. Он, как G5, ты, приближаясь к нему, скачиваешь все обновления и полностью преображаешься, заряжаясь его энергетикой. И моя огромная персональная благодарность АА за мою лучшую версию себя, as I am blessed that I live in the same times with such an outstanding personality. Юлия Первозванская 29.05.2021 Восторг! Были на спектакле в Санкт-Петербурге в Большом драматическом театре имени Г. А. Товстоногова. Интересное прочтение и современная интерпретация вечной темы особенно выделятся игра Хлестакова, необычные световые эффекты. Каждая, даже самая маленькая роль, не сыграна, а прожита. Спектакль потрясающий! Татьяна 13.04.2021 Замечательный спектакль! Как только на сцене появился Калягин А.А. Я не могла оторвать глаз от сцены. Он просто мастер своего дела. Поймала себя на мысли что он не играет, а на самом деле Хлестаков. Это пилотаж высшего уровня. Спасибо Вам за такое искусство! Хочется видеть Вас всегда и наслаждаться Вашим талантом! Анастасия 01.03.2019 Спасибо за прекрасный спектакль \"Ревизор\". Замечательная игра актёров, декорации, костюмы... Прекрасно провести вечер! Елисеева Кристина Александровна 28.02.2019 Александр Калягин провел спектакль как всегда на высоком уровне. Рекомендую к просмотру Sergey Niiforov 04.01.2019 К сожалению, нет таких слов, чтобы описать этот шедевр. Хотелось одного -чтобы спектакль не заканчивался. Ничего подобного не видел никогда. Если Елизавета Рыжих не станет лучшей актрисой РФ то я буду сильно удивлен и расстроен. Я весь спектакль не верил, что она молодая и симпатичная актриса, а то, как она двигается, можно принять за врожденный синдром инвалида. И только когда она вышла (точнее выплыла) на поклон я успокоился. Такой пластики нет ни у кого. Ей слова не нужны она все выражает телом. Браво всем!!!!!!. Андрей 15.12.2018 Это лучший спектакль, который я видела в своей жизни! Спасибо Александру Александровичу Калягину, всем замечательным актерам, игравшим в этом спектакле. Браво!!! Наталья 29.09.2017 Напишите нам: Представьтесь* E-mail* Ваш отзыв* *Поля, обязательные для заполнения © 2007–2023, Театр Et Cetera Официальный сайт Александра Калягина www.kalyagin.ru E-mail: theatre-etc@et-cetera.ru Адрес: 101000, Москва, Фролов пер., 2 Проезд: Метро «Тургеневская», «Чистые пруды», «Сретенский бульвар» Схема проезда Справки и заказ билетов по телефонам: +7 (495) 781-781-1 +7 (495) 625-48-47";
        String text = "Мальчики Работники Автомобили Собаки";
        words = LemmaConverter.splitContentIntoWords(text);
        wordLemmaMap = new HashMap<>();
        super.setUp();
    }

    public void testReturnWordIntoBaseForm() {
        long start = System.currentTimeMillis();
        long counter = 1;
        for(String word : words) {
            List<String> wordBaseForms = LemmaConverter.returnWordIntoBaseForm(word);
            if(!wordBaseForms.isEmpty() & !wordLemmaMap.containsKey(word)) {
                String resultWordForm = wordBaseForms.get(wordBaseForms.size() - 1);
                wordLemmaMap.put(word, resultWordForm);
                log.info("Добавлено слово '" + word + "' в коллекцию под номером " + counter++);
            }
        }

        var wordLemmaMapDebug = wordLemmaMap;
        String value = String.valueOf((double) (System.currentTimeMillis() - start) / 1000).substring(0, 4);
        double speedExecution = Double.parseDouble(value);
        System.out.println();
        assertTrue(speedExecution < 0.80);
    }

    // удаление данных
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}

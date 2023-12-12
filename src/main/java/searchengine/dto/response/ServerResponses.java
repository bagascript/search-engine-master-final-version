package searchengine.dto.response;

public interface ServerResponses
{
    String SITE_IS_NOT_AVAILABLE_ERROR_TEXT = "Главная страница сайта не доступна";
    String INVALID_URL_ERROR_TEXT = "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
    String INDEXATION_IS_ALREADY_RUNNING_ERROR_TEXT = "Индексация уже запущена";
    String INDEXATION_IS_STOPPED_BY_USER_TEXT = "Индексация остановлена пользователем";
    String INDEXATION_IS_NOT_RUNNING_ERROR_TEXT = "Индексация не запущена! Обновите страницу";
    String URL_EMPTY_ERROR_TEXT = "Страница не указана";

    String EMPTY_QUERY_ERROR_TEXT = "Задан пустой поисковый запрос";
    String SITE_IS_NOT_INDEXED_ERROR_TEXT = "Сайт/сайты ещё не проиндексированы";
}

package searchengine.dto.search;

import lombok.Data;

@Data
public class Link
{
    private String site;
    private String uri;
    private String content;
    private String title;
}

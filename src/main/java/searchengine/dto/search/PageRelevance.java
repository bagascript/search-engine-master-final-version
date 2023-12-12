package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import searchengine.model.PageEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageRelevance
{
    private PageEntity pageEntity;
    private float absoluteRank;
    private float maxRank;
}

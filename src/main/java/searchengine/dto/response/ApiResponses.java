package searchengine.dto.response;

import lombok.Data;
import searchengine.dto.search.DataProperties;
import searchengine.dto.statistics.StatisticsData;

import java.util.List;

@Data
public class ApiResponses {
    private boolean result;
    private String error;
    private int count;
    private List<DataProperties> data;
    private StatisticsData statistics;

    public ApiResponses() {
    }

    public ApiResponses(boolean result) {
        this.result = result;
    }

    public ApiResponses(boolean result, String error) {
        this.result = result;
        this.error = error;
    }

    public ApiResponses(boolean result, int count, List<DataProperties> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }
}

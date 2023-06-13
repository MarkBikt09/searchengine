package searchengine.dto.statistics;

import searchengine.model.Status;

import java.util.Date;

public record DetailedStatisticsItem(String url,
                                     String name,
                                     Status status,
                                     Date statusTime,
                                     String error,
                                     long pages,
                                     long lemmas) {
}

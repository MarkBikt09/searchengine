package searchengine.dto.statistics.response;

import searchengine.dto.statistics.SearchDto;

import java.util.List;

public record SearchResponse(boolean result,
                             int count,
                             List<SearchDto> data) {
}

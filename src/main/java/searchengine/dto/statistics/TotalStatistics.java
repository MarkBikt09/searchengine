package searchengine.dto.statistics;

public record TotalStatistics(Long sites,
                              Long pages,
                              Long lemmas,
                              boolean indexing) {
}


package searchengine.dto.statistics;

public record SearchDto(String site,
                        String siteName,
                        String uri,
                        String title,
                        String snippet,
                        Float relevance) {
}

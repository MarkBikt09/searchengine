package searchengine.services;

public interface IndexingService {
    boolean urlIndexing(String url);
    void indexingAll();
    boolean stopIndexing();
    void removeSiteFromIndex(String url);
}

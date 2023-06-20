package searchengine.services;

import searchengine.dto.statistics.response.Response;

public interface IndexingService {

    Response startIndexing(String url);
    Response indexPage(String url);
    boolean urlIndexing(String url);
    void indexingAll();
    Response stopIndexing();
    void removeSiteFromIndex(String url);
}

package searchengine.services;

import searchengine.dto.statistics.SearchDto;
import searchengine.dto.statistics.response.Response;

import java.util.List;

public interface SearchService {

    Response search(String query, String site, int offset, int limit);
    List<SearchDto> allSiteSearch(String text, int offset, int limit);
    List<SearchDto> siteSearch(String searchText, String url, int offset, int limit);

}

package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SearchDto;
import searchengine.dto.statistics.response.FalseResponse;
import searchengine.dto.statistics.response.Response;
import searchengine.dto.statistics.response.SearchResponse;
import searchengine.exception.NotAllSiteSearchException;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.morphology.Morphology;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.ClearHtmlCode;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final Morphology morphology;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;

    @Override
    public Response search(String query, String site, int offset, int limit) {
        if (query.isEmpty()) {
            return new FalseResponse(false, "Задан пустой поисковый запрос");
        }
        if (!site.isEmpty() && siteRepository.findByUrl(site) == null) {
            return new FalseResponse(false, "Указанная страница не найдена");
        }
        List<SearchDto> searchData;
        if (!site.isEmpty()) {
            searchData = siteSearch(query, site, offset, limit);
        } else {
            searchData = allSiteSearch(query, offset, limit);
        }
        return new SearchResponse(true, searchData.size(), searchData);
    }

    @Override
    public List<SearchDto> allSiteSearch(String searchText, int offset, int limit) {
        List<Site> siteList = siteRepository.findAll();
        List<Lemma> foundLemmaList = new ArrayList<>();
        List<String> textLemmaList = getLemmaFromSearchText(searchText);
        siteList.forEach(site -> foundLemmaList.addAll(getLemmaListFromSite(textLemmaList, site)));
        List<SearchDto> searchData = new ArrayList<>();
        for (int i = 0; i < foundLemmaList.size(); i++) {
            if (textLemmaList.contains(foundLemmaList.get(i).getLemma())) {
                searchData.addAll(getSearchDtoList(foundLemmaList, textLemmaList, offset, limit));
            } else {
                throw new NotAllSiteSearchException();
            }
        }
        searchData = searchData.stream().distinct().sorted((o1, o2) -> Float.compare(o2.relevance(), o1.relevance())).toList();
        return searchData;
    }

    @Override
    public List<SearchDto> siteSearch(String searchText, String url, int offset, int limit) {
        Site site = siteRepository.findByUrl(url);
        List<String> textLemmaList = getLemmaFromSearchText(searchText);
        List<Lemma> foundLemmaList = getLemmaListFromSite(textLemmaList, site);
        return getSearchDtoList(foundLemmaList, textLemmaList, offset, limit);
    }

    private List<String> getLemmaFromSearchText(String searchText) {
        String[] words = searchText.toLowerCase(Locale.ROOT).split(" ");
        List<String> lemmaList = new ArrayList<>();
        for (String lemma : words) {
            List<String> list = morphology.getLemma(lemma);
            lemmaList.addAll(list);
        }
        return lemmaList;
    }

    private List<Lemma> getLemmaListFromSite(List<String> lemmas, Site site) {
        lemmaRepository.flush();
        List<Lemma> lemmaList = lemmaRepository.findLemmaListBySite(lemmas, site);
        List<Lemma> result = new ArrayList<>(lemmaList);
        result.sort(Comparator.comparingInt(Lemma::getFrequency));
        return result;
    }

    private List<SearchDto> getSearchData(Hashtable<Page, Float> pageList, List<String> textLemmaList) {
        return pageList.keySet().stream()
                .map(page -> {
                    String uri = page.getPath();
                    String content = page.getContent();
                    Site pageSite = page.getSiteId();
                    String site = pageSite.getUrl();
                    String siteName = pageSite.getName();
                    Float absRelevance = pageList.get(page);

                    StringBuilder clearContent = new StringBuilder();
                    String title = ClearHtmlCode.clear(content, "title");
                    String body = ClearHtmlCode.clear(content, "body");
                    clearContent.append(title).append(" ").append(body);
                    String snippet = getSnippet(clearContent.toString(), textLemmaList);

                    return snippet.isEmpty() ? null : new SearchDto(site, siteName, uri, title, snippet, absRelevance);
                })
                .toList();
    }

    private String getSnippet(String content, List<String> lemmaList) {
        Map<String, List<Integer>> lemmaIndexMap = new HashMap<>();
        StringBuilder result = new StringBuilder();
        for (String lemma : lemmaList) {
            lemmaIndexMap.put(lemma, morphology.findLemmaIndexInText(content, lemma));
        }
        Map<String, List<String>> wordsListMap = getWordsFromContent(content, lemmaIndexMap);
        for (Map.Entry<String, List<String>> entry : wordsListMap.entrySet()) {
            if (entry.getValue().isEmpty()) {
                return "";
            }
            for (String word : entry.getValue()) {
                content = content.replaceAll(word, "<b>" + word + "</b>");
            }
            for (int i = 0; i < entry.getValue().size(); i++) {
                String word = "<b>" + entry.getValue().get(i) + "</b>";
                int start = content.indexOf(word);

                if (start != -1) {
                    String res = content.substring(start, start + 50);
                    result.append(res).append("...");
                }
            }
        }
        return result.toString();
    }

    private Map<String, List<String>> getWordsFromContent(String content, Map<String, List<Integer>> lemmaIndexMap) {
        Map<String, List<String>> resultMap = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : lemmaIndexMap.entrySet()) {
            List<Integer> lemmaIndex = new ArrayList<>(entry.getValue());
            Collections.sort(lemmaIndex);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < lemmaIndex.size(); i++) {
                int start = lemmaIndex.get(i);
                int end = content.indexOf(" ", start);
                int nextIndex = i + 1;
                while (nextIndex < lemmaIndex.size() && lemmaIndex.get(nextIndex) - end > 0 && lemmaIndex.get(nextIndex) - end < 5) {
                    end = content.indexOf(" ", lemmaIndex.get(nextIndex));
                    nextIndex++;
                }
                i = nextIndex - 1;
                String word = getWordsFromIndex(start, end, content);
                result.add(word);
            }
            resultMap.put(entry.getKey(),
                    result.stream().distinct()
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .toList());
        }
        return resultMap;

    }

    private String getWordsFromIndex(int start, int end, String content) {
        return content.substring(start, end);
    }

    private List<SearchDto> getSearchDtoList(List<Lemma> lemmaList, List<String> textLemmaList, int offset, int limit) {
        List<SearchDto> result = new ArrayList<>();
        pageRepository.flush();
        if (lemmaList.size() >= textLemmaList.size()) {
            Set<Page> foundPageSet = new HashSet<>(pageRepository.findByLemmaList(lemmaList));
            indexRepository.flush();
            List<Page> foundPageList = new ArrayList<>(foundPageSet);
            List<Index> foundIndexList = indexRepository.findByPagesAndLemmas(lemmaList, foundPageList);
            Hashtable<Page, Float> sortedPageByAbsRelevance = getPageAbsRelevance(foundPageList, foundIndexList);
            List<SearchDto> dataList = getSearchData(sortedPageByAbsRelevance, textLemmaList);

            if (offset > dataList.size()) {
                return new ArrayList<>();
            }

            if (dataList.size() > limit) {
                for (int i = offset; i < limit; i++) {
                    if (dataList.get(i) != null) {
                        result.add(dataList.get(i));
                    }
                }
                return result;
            } else {
                for (SearchDto dto : dataList) {
                    if (dto != null) {
                        result.add(dto);
                    }
                }
                return result;
            }
        } else return result;
    }

    private Hashtable<Page, Float> getPageAbsRelevance(List<Page> pageList, List<Index> indexList) {
        Map<Page, Float> pageWithRelevance = pageList.stream()
                .collect(Collectors.toMap(page -> page, page -> indexList.stream()
                        .filter(index -> index.getPage().equals(page))
                        .map(Index::getRank)
                        .reduce(0f, Float::sum)));

        Map<Page, Float> pageWithAbsRelevance = pageWithRelevance.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue() / Collections.max(pageWithRelevance.values())));

        return pageWithAbsRelevance.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, Hashtable::new));
    }
}

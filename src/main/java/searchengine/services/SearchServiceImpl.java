package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SearchDto;
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
    public List<SearchDto> allSiteSearch(String searchText, int offset, int limit) {
        List<Site> siteList = siteRepository.findAll();
        List<SearchDto> result = new ArrayList<>();
        List<Lemma> foundLemmaList = new ArrayList<>();
        List<String> textLemmaList = getLemmaFromSearchText(searchText);
        for (Site site : siteList) {
            foundLemmaList.addAll(getLemmaListFromSite(textLemmaList, site));
        }
        List<SearchDto> searchData = null;
        for (Lemma l : foundLemmaList) {
            if (l.getLemma().equals(searchText)) {
                searchData = new ArrayList<>(getSearchDtoList(foundLemmaList, textLemmaList, offset, limit));
                searchData.sort((o1, o2) -> Float.compare(o2.relevance(), o1.relevance()));
                if (searchData.size() > limit) {
                    for (int i = offset; i < limit; i++) {
                        result.add(searchData.get(i));
                    }
                    return result;
                }
            } else {
                throw new NotAllSiteSearchException();
            }
        }
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

                    return new SearchDto(site, siteName, uri, title, snippet, absRelevance);
                })
                .toList();
    }

    private String getSnippet(String content, List<String> lemmaList) {
        List<Integer> lemmaIndex = new ArrayList<>();
        StringBuilder result = new StringBuilder();
        for (String lemma : lemmaList) {
            lemmaIndex.addAll(morphology.findLemmaIndexInText(content, lemma));
        }
        Collections.sort(lemmaIndex);
        List<String> wordsList = getWordsFromContent(content, lemmaIndex);
        for (int i = 0; i < wordsList.size(); i++) {
            result.append(wordsList.get(i)).append("... ");
            if (i > 3) {
                break;
            }
        }
        return result.toString();
    }

    private List<String> getWordsFromContent(String content, List<Integer> lemmaIndex) {
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
        return result.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private String getWordsFromIndex(int start, int end, String content) {
        String word = content.substring(start, end);
        int prevPoint;
        int lastPoint;
        if (content.lastIndexOf(" ", start) != -1) {
            prevPoint = content.lastIndexOf(" ", start);
        } else prevPoint = start;
        if (content.indexOf(" ", end + 30) != -1) {
            lastPoint = content.indexOf(" ", end + 30);
        } else lastPoint = content.indexOf(" ", end);
        String text = content.substring(prevPoint, lastPoint);
        try {
            text = text.replaceAll(word, "<b>" + word + "</b>");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return text;
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
                    result.add(dataList.get(i));
                }
                return result;
            } else return dataList;
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

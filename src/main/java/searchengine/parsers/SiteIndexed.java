package searchengine.parsers;

import lombok.RequiredArgsConstructor;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexDto;
import searchengine.dto.statistics.LemmaDto;
import searchengine.dto.statistics.PageDto;
import searchengine.exception.NoLemmasPageException;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;

@RequiredArgsConstructor
public class SiteIndexed implements Runnable {

    private static final int PROCESSOR_CORE_COUNT = Runtime.getRuntime().availableProcessors();
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaParser lemmaParser;
    private final IndexParser indexParser;
    private final String url;
    private final SitesList sitesList;


    @Override
    public void run() {
        saveDateSite();
        try {
            List<PageDto> pageDtoList = getPageDtoList();
            saveToBase(pageDtoList);
            getLemmasPage();
            indexingWords();

        } catch (InterruptedException e) {
            errorSite();
            Thread.currentThread().interrupt();
        }
    }

    private List<PageDto> getPageDtoList() throws InterruptedException {
        if (!Thread.interrupted()) {
            String urlFormat = url + "/";
            List<PageDto> pageDtoVector = new ArrayList<>();
            List<String> urlList = new ArrayList<>();
            ForkJoinPool forkJoinPool = new ForkJoinPool(PROCESSOR_CORE_COUNT);
            List<PageDto> pages = forkJoinPool.invoke(new PageUrlParser(urlFormat, pageDtoVector, urlList));
            return new CopyOnWriteArrayList<>(pages);
        } else throw new InterruptedException();
    }

    private void saveToBase(List<PageDto> pages) throws InterruptedException {
        if (!Thread.interrupted()) {
            Site site = siteRepository.findByUrl(url);
            List<Page> pageList = new ArrayList<>(pages.size());

            for (PageDto page : pages) {
                int start = page.url().indexOf(url) + url.length();
                String pageFormat = page.url().substring(start);
                pageList.add(new Page(site, pageFormat, page.code(), page.content()));
            }

            pageRepository.saveAll(pageList);
            pageRepository.flush();
        } else {
            throw new InterruptedException();
        }
    }

    private void getLemmasPage() {
        if (!Thread.interrupted()) {
            Site siteEntity = siteRepository.findByUrl(url);
            siteEntity.setStatusTime(new Date());
            lemmaParser.run(siteEntity);
            List<LemmaDto> lemmaDtoList = lemmaParser.getLemmaDtoList();
            List<Lemma> lemmaList = new ArrayList<>(lemmaDtoList.size());

            for (LemmaDto lemmaDto : lemmaDtoList) {
                lemmaList.add(new Lemma(lemmaDto.lemma(), lemmaDto.frequency(), siteEntity));
            }

            lemmaRepository.saveAll(lemmaList);
            lemmaRepository.flush();
        } else {
            throw new NoLemmasPageException();
        }
    }

    private void indexingWords() throws InterruptedException {
        if (!Thread.interrupted()) {
            Site site = siteRepository.findByUrl(url);
            indexParser.run(site);
            List<IndexDto> indexDtoList = indexParser.getIndexList();
            List<Index> indexList = new ArrayList<>(indexDtoList.size());
            site.setStatusTime(new Date());

            for (IndexDto indexDto : indexDtoList) {
                Page page = pageRepository.getReferenceById(indexDto.pageID());
                Lemma lemma = lemmaRepository.getReferenceById(indexDto.lemmaID());
                indexList.add(new Index(page, lemma, indexDto.rank()));
            }

            indexRepository.saveAll(indexList);
            indexRepository.flush();
            site.setStatusTime(new Date());
            site.setStatus(Status.INDEXED);
            siteRepository.save(site);

        } else {
            throw new InterruptedException();
        }
    }

    private void saveDateSite() {
        Site site = new Site();
        site.setUrl(url);
        site.setName(getName());
        site.setStatus(Status.INDEXING);
        site.setStatusTime(new Date());
        siteRepository.flush();
        siteRepository.save(site);
    }

    private void errorSite() {
        Site site = new Site();
        site.setLastError("Индексация остановлена");
        site.setStatus(Status.FAILED);
        site.setStatusTime(new Date());
        siteRepository.save(site);
    }

    private String getName() {
        List<searchengine.config.Site> sites = sitesList.getSites();
        for (searchengine.config.Site map : sites) {
            if (map.getUrl().equals(url)) {
                return map.getName();
            }
        }
        return "";
    }
}


package searchengine.parsers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import searchengine.dto.statistics.LemmaDto;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.morphology.Morphology;
import searchengine.repositories.PageRepository;
import searchengine.utils.ClearHtmlCode;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
public class LemmaBuild implements LemmaParser {
    private final PageRepository pageRepository;
    private final Morphology morphology;
    private List<LemmaDto> lemmaDtoList;

    @Override
    public void run(Site site) {
        lemmaDtoList = new CopyOnWriteArrayList<>();
        Iterable<Page> pageList = pageRepository.findAll();
        TreeMap<String, Integer> lemmaList = new TreeMap<>();
        for (Page page : pageList) {
            String content = page.getContent();
            String title = ClearHtmlCode.clear(content, "title");
            String body = ClearHtmlCode.clear(content, "body");
            HashMap<String, Integer> titleList = morphology.getLemmaList(title);
            HashMap<String, Integer> bodyList = morphology.getLemmaList(body);
            Set<String> allWords = new HashSet<>();
            allWords.addAll(titleList.keySet());
            allWords.addAll(bodyList.keySet());
            for (String word : allWords) {
                int frequency = lemmaList.getOrDefault(word, 0) + 1;
                lemmaList.put(word, frequency);
            }
        }
        for (String lemma : lemmaList.keySet()) {
            Integer frequency = lemmaList.get(lemma);
            lemmaDtoList.add(new LemmaDto(lemma, frequency));
        }
    }

    @Override
    public List<LemmaDto> getLemmaDtoList() {
        return lemmaDtoList;
    }
}

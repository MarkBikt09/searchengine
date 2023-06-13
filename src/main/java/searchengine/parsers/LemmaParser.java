package searchengine.parsers;

import searchengine.dto.statistics.LemmaDto;
import searchengine.model.Site;

import java.util.List;

public interface LemmaParser {
    void run(Site site);
    List<LemmaDto> getLemmaDtoList();
}

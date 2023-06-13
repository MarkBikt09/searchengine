package searchengine.repositories;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long> {

    @Cacheable("indexesByLemmasAndPages")
    default List<Index> findByPagesAndLemmas(List<Lemma> lemmaList, List<Page> pageList) {
        Set<Long> lemmaIds = lemmaList.stream().map(Lemma::getId).collect(Collectors.toSet());
        Set<Long> pageIds = pageList.stream().map(Page::getId).collect(Collectors.toSet());

        return findAllByLemmaIdInAndPageIdIn(lemmaIds, pageIds);
    }

    List<Index> findAllByLemmaIdInAndPageIdIn(Set<Long> lemmaIds, Set<Long> pageIds);
}
package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Collection;
import java.util.Set;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {
    long countBySiteId(Site site);

    Iterable<Page> findBySiteId(Site site);

    @Query(value = "SELECT DISTINCT p.* FROM Page p JOIN Words_index i ON p.id = i.page_id WHERE i.lemma_id IN :lemmas", nativeQuery = true)
    Set<Page> findByLemmaList(@Param("lemmas") Collection<Lemma> lemmaList);
}

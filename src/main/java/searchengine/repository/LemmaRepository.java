package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import javax.transaction.Transactional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer>
{

    @Transactional
    boolean existsByLemmaAndSiteId(String lemma, int siteId);

    @Transactional
    void deleteByLemmaAndSiteId(String lemma, int siteId);

    @Transactional
    @Query("SELECT l.frequency FROM LemmaEntity l WHERE l.lemma = :lemma AND l.site = :site")
    int getFrequencyByLemmaAndSiteId(@Param("lemma") String lemma, @Param("site") SiteEntity site);

    @Transactional
    @Query(value = "SELECT l FROM LemmaEntity l WHERE l.lemma = :lemma AND l.site = :site")
    LemmaEntity getLemmaEntity(@Param("lemma") String lemma, @Param("site") SiteEntity siteId);

    @Modifying
    @Transactional
    @Query("UPDATE LemmaEntity l SET l.frequency = :frequency WHERE l.id = :id")
    void updateFrequency(@Param("id") int id, int frequency);

    @Transactional
    @Query(value = "SELECT COUNT(*) FROM search_engine.lemma l WHERE l.site_id = :site_id", nativeQuery = true)
    int countAllLemmasBySiteId(@Param("site_id") SiteEntity siteEntity);
}

package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;


import javax.transaction.Transactional;
import java.util.List;


public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    @Transactional
    boolean existsByPageIdAndLemmaId(int pageId, int lemmaId);

    @Modifying
    @Transactional
    @Query("UPDATE IndexEntity i SET i.rank = :rank WHERE i.id = :id")
    void updateIndexRank(@Param("id") int id, @Param("rank") float rank);

    @Transactional
    @Query("SELECT i.id FROM IndexEntity i WHERE i.page = :page")
    List<Integer> findIndexesByPageId(@Param("page") PageEntity pageEntity);

    @Transactional
    List<IndexEntity> findAllByLemmaId(int lemmaId);

    @Transactional
    IndexEntity findByLemmaIdAndPageId(int lemmaId, int pageId);
}

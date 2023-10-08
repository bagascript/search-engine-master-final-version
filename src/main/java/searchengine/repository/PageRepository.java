package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import javax.transaction.Transactional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    @Async
    @Transactional
    @Query(value = "SELECT p.path FROM search_engine.page p " +
            "WHERE p.site_id = :site_id ORDER BY id DESC LIMIT 1", nativeQuery = true)
    String getLastUrlBySiteId(@Param("site_id") SiteEntity site);

    @Transactional
    PageEntity findById(int id);

    @Async
    @Transactional
    boolean existsByPath(String path);

    @Transactional
    @Query("SELECT p FROM PageEntity p WHERE p.path = :path")
    PageEntity findByPath(@Param("path") String path);

    @Transactional
    @Query(value = "SELECT p.content FROM search_engine.page p " +
            "WHERE p.path = :path", nativeQuery = true)
    String getContentByPath(@Param("path") String path);

    @Transactional
    @Query(value = "SELECT COUNT(*) FROM search_engine.page p WHERE p.site_id = :site_id", nativeQuery = true)
    int countAllPagesBySiteId(@Param("site_id") SiteEntity siteEntity);
}

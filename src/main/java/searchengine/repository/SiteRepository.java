package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;
import searchengine.model.enums.StatusType;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer>
{
    @Modifying
    @Transactional
    @Query("UPDATE SiteEntity s SET s.statusTime = now() WHERE s.id = :id")
    void updateStatusTime(@Param("id") int id);

    @Modifying
    @Transactional
    @Query("UPDATE SiteEntity s SET s.status = :status WHERE s.id = :id")
    void updateOnIndexed(@Param("id") int id, StatusType status);

    @Modifying
    @Transactional
    @Query("UPDATE SiteEntity s SET s.status = :status, s.lastError = :lastError WHERE s.id = :id")
    void updateOnFailed(@Param("id") int id, StatusType status, String lastError);

    @Modifying
    @Transactional
    List<SiteEntity> findAllByStatus(StatusType statusType);

    @Transactional
    SiteEntity findByUrl(String url);

    @Transactional
    boolean existsByUrl(String url);
}

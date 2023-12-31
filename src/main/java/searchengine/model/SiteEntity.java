package searchengine.model;

import lombok.*;
import searchengine.model.enums.StatusType;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

@Getter
@Setter
@Entity
@Table(name = "site")
@NoArgsConstructor
@AllArgsConstructor
public class SiteEntity implements Serializable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "site", cascade = CascadeType.ALL)
    private List<PageEntity> pageEntities = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "site", cascade = CascadeType.ALL)
    private List<LemmaEntity> lemmaEntities = new ArrayList<>();

    @Column(name = "status", nullable = false, columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')")
    @Enumerated(EnumType.STRING)
    private StatusType status;

    @Column(name = "status_time", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "url", nullable = false, columnDefinition = "VARCHAR(255)")
    private String url;

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(255)")
    private String name;
}

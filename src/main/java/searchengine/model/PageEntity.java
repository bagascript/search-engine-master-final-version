package searchengine.model;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "page")
@NoArgsConstructor
@AllArgsConstructor
public class PageEntity implements Serializable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false)
    private SiteEntity site;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL)
    private Set<IndexEntity> indexEntities;

    @Column(name = "path", columnDefinition = "TEXT NOT NULL, Index(path(255))")
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT NOT NULL")
    private String content;
}

package searchengine.model;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Entity
@Table(name = "site")
public class Site implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "status_time")
    private Date statusTime;

    @Column(name = "last_error")
    private String lastError;

    private String url;

    private String name;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "siteId", cascade = CascadeType.ALL)
    protected List<Page> pageEntityList = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "siteEntityId", cascade = CascadeType.ALL)
    protected List<Lemma> lemmaEntityList = new ArrayList<>();
}
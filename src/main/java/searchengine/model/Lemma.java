package searchengine.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "lemma", indexes = {@javax.persistence.Index(name = "lemma_list", columnList = "lemma")})
@NoArgsConstructor
public class Lemma implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", referencedColumnName = "id")
    private Site siteEntityId;
    private String lemma;
    private int frequency;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL)
    private List<Index> index = new ArrayList<>();

    public Lemma(String lemma, int frequency, Site siteEntityId) {
        this.lemma = lemma;
        this.frequency = frequency;
        this.siteEntityId = siteEntityId;
    }
}

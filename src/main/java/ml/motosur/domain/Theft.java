package ml.motosur.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** Déclaration de vol d'une moto, et sa résolution éventuelle. */
@Entity
@Table(name = "thefts")
public class Theft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "moto_id")
    public Moto moto;

    @Column(nullable = false, unique = true, length = 30)
    public String reference;

    @Column(nullable = false)
    public LocalDate theftDate;

    @Column(nullable = false, length = 40)
    public String commune;

    @Column(nullable = false, length = 200)
    public String place;

    @Column(length = 1000)
    public String details;

    @Column(nullable = false)
    public Instant reportedAt = Instant.now();

    public Instant recoveredAt;

    @Column(length = 30)
    public String recoveredReference;

    @Column(length = 150)
    public String recoveredCommissariat;

    @Column(length = 40)
    public String recoveredCommune;
}

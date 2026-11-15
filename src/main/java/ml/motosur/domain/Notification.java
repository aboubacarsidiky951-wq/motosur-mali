package ml.motosur.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Message destiné au propriétaire d'une moto, consultable sur le site (cloche de notification). */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "moto_id")
    public Moto moto;

    @Column(nullable = false, length = 600)
    public String message;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public boolean seen;
}

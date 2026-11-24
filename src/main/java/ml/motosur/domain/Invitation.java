package ml.motosur.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Lien d'invitation à usage unique : la personne choisit son mot de passe, puis le lien ne sert plus. */
@Entity
@Table(name = "invitations")
public class Invitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id")
    public Account account;

    /** Empreinte SHA-256 du jeton : le jeton lui-même n'est jamais stocké. */
    @Column(nullable = false, unique = true, length = 64)
    public String tokenHash;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant expiresAt;

    /** Renseigné quand le lien est utilisé (ou annulé par un nouveau lien). */
    public Instant usedAt;

    @Column(length = 120)
    public String createdBy;
}

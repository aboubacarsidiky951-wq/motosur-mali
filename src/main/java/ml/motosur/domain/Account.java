package ml.motosur.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Email (maintenance, direction, commissariat) ou matricule (agent). */
    @Column(nullable = false, unique = true, length = 120)
    public String login;

    @Column(nullable = false, length = 100)
    public String passwordHash;

    @Column(nullable = false, length = 150)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Role role;

    @Column(length = 150)
    public String commissariat;

    /** Téléphone (8 chiffres) : sert d'identifiant de connexion et pour envoyer le lien d'invitation. */
    @Column(length = 20)
    public String phone;

    /** Bureau ou section, pour les comptes du bureau d'enregistrement. */
    @Column(length = 80)
    public String section;

    /** Vrai tant que la personne n'a pas ouvert son lien d'invitation. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    public boolean pending;

    @Column(nullable = false)
    public boolean active = true;

    @Column(nullable = false)
    public boolean mustChangePassword;

    @Column(nullable = false)
    public int failedAttempts;

    public Instant lockedUntil;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Instant lastLoginAt;

    /** Double vérification (comptes de maintenance) : clé secrète TOTP, active seulement après confirmation. */
    @Column(length = 64)
    public String totpSecret;

    @Column(nullable = false, columnDefinition = "boolean default false")
    public boolean totpEnabled;

    /** Dernier pas de 30 s accepté : un même code ne peut pas servir deux fois. */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    public long lastTotpStep;
}

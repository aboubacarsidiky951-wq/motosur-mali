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
}

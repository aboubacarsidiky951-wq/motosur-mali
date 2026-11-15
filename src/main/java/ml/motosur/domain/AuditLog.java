package ml.motosur.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Journal des actions sensibles (connexions, comptes, changements). */
@Entity
@Table(name = "audit_log", indexes = @Index(name = "idx_audit_at", columnList = "at"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Instant at = Instant.now();

    @Column(nullable = false, length = 120)
    public String actor;

    @Column(nullable = false, length = 40)
    public String action;

    @Column(length = 500)
    public String detail;

    @Column(length = 64)
    public String ip;
}

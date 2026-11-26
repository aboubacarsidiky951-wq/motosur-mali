package ml.motosur.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Historique des changements de propriétaire d'une moto (vente), faits au bureau. */
@Entity
@Table(name = "ownership_transfers", indexes = @Index(name = "idx_transfer_moto", columnList = "moto_id"))
public class OwnershipTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "moto_id")
    public Moto moto;

    @Column(nullable = false, length = 150)
    public String fromName;

    @Column(nullable = false, length = 20)
    public String fromPhone;

    @Column(nullable = false, length = 150)
    public String toName;

    @Column(nullable = false, length = 20)
    public String toPhone;

    @Column(nullable = false, length = 40)
    public String toCommune;

    @Column(length = 80)
    public String section;

    @Column(length = 120)
    public String agentLogin;

    @Column(length = 150)
    public String agentName;

    @Column(length = 150)
    public String checkedBy;

    public Integer paidAmount;

    @Column(length = 20)
    public String paymentMode;

    @Column(length = 30)
    public String receiptNo;

    @Column(nullable = false)
    public Instant at = Instant.now();
}

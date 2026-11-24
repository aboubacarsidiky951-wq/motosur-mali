package ml.motosur.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Moto enregistrée. Plaque, châssis et moteur sont stockés normalisés (majuscules, sans espaces). */
@Entity
@Table(name = "motos")
public class Moto {

    public enum Status { OK, STOLEN, RECOVERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 30)
    public String reference;

    @Column(nullable = false, unique = true, length = 30)
    public String plate;

    @Column(nullable = false, unique = true, length = 40)
    public String chassis;

    @Column(nullable = false, unique = true, length = 40)
    public String engine;

    @Column(nullable = false, length = 60)
    public String brand;

    @Column(nullable = false, length = 60)
    public String model;

    @Column(nullable = false, length = 30)
    public String color;

    @Column(name = "model_year", nullable = false)
    public int year;

    @Column(nullable = false, length = 150)
    public String ownerName;

    @Column(nullable = false, length = 20)
    public String ownerPhone;

    @Column(nullable = false, length = 40)
    public String commune;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    public Status status = Status.OK;

    @Column(nullable = false)
    public Instant registeredAt = Instant.now();

    /** Code secret à 4 chiffres remis au propriétaire sur son reçu (stocké chiffré, jamais en clair). */
    @Column(length = 80)
    public String pinHash;

    @Column(nullable = false, columnDefinition = "integer default 0")
    public int pinFails;

    public Instant pinLockedUntil;

    /** Enregistrement au bureau : qui, où, combien. */
    @Column(length = 80)
    public String section;
    @Column(length = 120)
    public String registeredByLogin;
    @Column(length = 150)
    public String registeredByName;
    @Column(length = 150)
    public String checkedBy;
    public Integer paidAmount;
    @Column(length = 20)
    public String paymentMode;
    @Column(length = 30)
    public String receiptNo;
}

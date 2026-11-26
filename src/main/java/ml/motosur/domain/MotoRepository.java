package ml.motosur.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MotoRepository extends JpaRepository<Moto, Long> {
    long countByStatus(Moto.Status status);

    Optional<Moto> findFirstByPlateOrChassisOrEngine(String plate, String chassis, String engine);

    Optional<Moto> findByReference(String reference);
    java.util.List<Moto> findByPaidAmountNotNullOrderByRegisteredAtDesc();
    java.util.List<Moto> findTop30ByRegisteredByLoginOrderByRegisteredAtDesc(String login);
    boolean existsByReference(String reference);
    boolean existsByPlate(String plate);

    boolean existsByChassis(String chassis);

    boolean existsByEngine(String engine);

    /** Recherche partielle (police) : plaque, châssis ou moteur contenant le texte, déjà normalisé. */
    @Query("select m from Moto m where m.plate like concat('%', :n, '%') or m.chassis like concat('%', :n, '%') "
            + "or m.engine like concat('%', :n, '%') order by m.registeredAt desc")
    List<Moto> search(@Param("n") String n, Pageable page);
}

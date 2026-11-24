package ml.motosur.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}

package ml.motosur.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MotoRepository extends JpaRepository<Moto, Long> {
    long countByStatus(Moto.Status status);

    Optional<Moto> findFirstByPlateOrChassisOrEngine(String plate, String chassis, String engine);

    boolean existsByPlate(String plate);

    boolean existsByChassis(String chassis);

    boolean existsByEngine(String engine);
}

package ml.motosur.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheftRepository extends JpaRepository<Theft, Long> {
    Optional<Theft> findFirstByMotoOrderByReportedAtDesc(Moto moto);
}

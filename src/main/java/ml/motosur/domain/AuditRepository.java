package ml.motosur.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByAtDesc(Pageable pageable);

    long countByActionAndAtAfter(String action, Instant after);
}

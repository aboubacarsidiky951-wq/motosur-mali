package ml.motosur.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop20ByMotoOrderByCreatedAtDesc(Moto moto);
}

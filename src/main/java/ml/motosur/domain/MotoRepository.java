package ml.motosur.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MotoRepository extends JpaRepository<Moto, Long> {
    long countByStatus(Moto.Status status);
}

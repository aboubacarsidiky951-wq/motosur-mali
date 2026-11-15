package ml.motosur.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TheftRepository extends JpaRepository<Theft, Long> {
    Optional<Theft> findFirstByMotoOrderByReportedAtDesc(Moto moto);

    @Query("select t from Theft t join fetch t.moto order by t.reportedAt desc")
    List<Theft> findAllWithMoto();
}

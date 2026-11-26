package ml.motosur.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnershipTransferRepository extends JpaRepository<OwnershipTransfer, Long> {
    List<OwnershipTransfer> findAllByOrderByAtDesc();

    List<OwnershipTransfer> findTop30ByAgentLoginOrderByAtDesc(String agentLogin);
}

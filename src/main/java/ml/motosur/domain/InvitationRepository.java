package ml.motosur.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByTokenHash(String tokenHash);
    List<Invitation> findByAccountAndUsedAtIsNull(Account account);
    Optional<Invitation> findFirstByAccountOrderByCreatedAtDesc(Account account);
}

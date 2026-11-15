package ml.motosur.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    long countByRole(Role role);
}

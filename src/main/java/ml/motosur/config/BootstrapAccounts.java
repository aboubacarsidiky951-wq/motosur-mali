package ml.motosur.config;

import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.Role;
import ml.motosur.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée le compte de maintenance au premier démarrage, à partir des variables d'environnement
 * MAINTENANCE_EMAIL et MAINTENANCE_PASSWORD. Si le compte existe déjà, son mot de passe n'est jamais modifié.
 */
@Component
public class BootstrapAccounts implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAccounts.class);

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final String email;
    private final String password;

    public BootstrapAccounts(AccountRepository accounts, PasswordEncoder encoder, AuditService audit,
                             @Value("${motosur.maintenance.email:}") String email,
                             @Value("${motosur.maintenance.password:}") String password) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(String... args) {
        if (email.isEmpty() || password.isEmpty()) {
            log.warn("MAINTENANCE_EMAIL / MAINTENANCE_PASSWORD absents : aucun compte de maintenance créé.");
            return;
        }
        if (password.length() < 12) {
            log.error("MAINTENANCE_PASSWORD trop court (12 caractères minimum) : compte non créé.");
            return;
        }
        if (accounts.existsByLoginIgnoreCase(email)) {
            return;
        }
        Account a = new Account();
        a.login = email.toLowerCase();
        a.passwordHash = encoder.encode(password);
        a.name = "Maintenance";
        a.role = Role.MAINTENANCE;
        a.mustChangePassword = false;
        accounts.save(a);
        audit.log("système", "ACCOUNT_CREATED", "Compte de maintenance créé : " + a.login, null);
        log.info("Compte de maintenance créé pour {}", a.login);
    }
}

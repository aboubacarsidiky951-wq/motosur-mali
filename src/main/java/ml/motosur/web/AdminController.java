package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.Role;
import ml.motosur.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * Gestion des comptes par la hiérarchie :
 * la Direction générale crée les commissariats et les agents ; un commissariat crée les agents de son commissariat.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern MATRICULE = Pattern.compile("^[A-Za-z0-9._-]{3,40}$");
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record CreateAccount(@NotBlank @Size(max = 120) String login, @NotBlank @Size(max = 150) String name,
                                @NotNull Role role, @Size(max = 150) String commissariat) {}

    public record ActiveRequest(boolean active) {}

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminController(AccountRepository accounts, PasswordEncoder encoder, AuditService audit) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
    }

    private Account me() {
        String login = SecurityContextHolder.getContext().getAuthentication().getName();
        return accounts.findByLoginIgnoreCase(login).orElseThrow();
    }

    /** Ce compte est-il sous la responsabilité de l'utilisateur connecté ? */
    private static boolean manages(Account who, Account target) {
        if (target.role != Role.COMMISSARIAT && target.role != Role.AGENT) return false;
        if (who.role == Role.CENTRAL) return true;
        return who.role == Role.COMMISSARIAT && target.role == Role.AGENT
                && who.commissariat != null && who.commissariat.equals(target.commissariat);
    }

    private static String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    @GetMapping("/accounts")
    public List<MaintenanceController.AccountView> list() {
        Account who = me();
        return accounts.findAll().stream().filter(a -> manages(who, a)).map(MaintenanceController.AccountView::of).toList();
    }

    @PostMapping("/accounts")
    public ResponseEntity<?> create(@Valid @RequestBody CreateAccount r, HttpServletRequest req) {
        Account who = me();
        Role role = r.role();
        String commissariat;
        if (who.role == Role.CENTRAL) {
            if (role != Role.COMMISSARIAT && role != Role.AGENT) return ResponseEntity.badRequest().body(Map.of("error", "ROLE_NOT_ALLOWED"));
            commissariat = r.commissariat() == null ? "" : r.commissariat().trim();
        } else if (who.role == Role.COMMISSARIAT) {
            if (role != Role.AGENT) return ResponseEntity.badRequest().body(Map.of("error", "ROLE_NOT_ALLOWED"));
            commissariat = who.commissariat == null ? "" : who.commissariat;
        } else {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN"));
        }
        if (commissariat.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "COMMISSARIAT_REQUIRED"));

        String login = r.login().trim();
        if (role == Role.AGENT) {
            if (!MATRICULE.matcher(login).matches()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_LOGIN"));
            login = login.toUpperCase();
        } else {
            if (!EMAIL.matcher(login).matches()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_LOGIN"));
            login = login.toLowerCase();
        }
        if (accounts.existsByLoginIgnoreCase(login)) return ResponseEntity.status(409).body(Map.of("error", "ALREADY_EXISTS"));

        String temp = temporaryPassword();
        Account a = new Account();
        a.login = login;
        a.name = r.name().trim();
        a.role = role;
        a.commissariat = commissariat;
        a.passwordHash = encoder.encode(temp);
        a.mustChangePassword = true;
        accounts.save(a);
        audit.log(who.login, "ACCOUNT_CREATED", a.role + " " + a.login + " (" + commissariat + ")", req.getRemoteAddr());
        return ResponseEntity.status(201).body(Map.of("account", MaintenanceController.AccountView.of(a), "temporaryPassword", temp));
    }

    @PostMapping("/accounts/{id}/active")
    public ResponseEntity<?> setActive(@PathVariable Long id, @RequestBody ActiveRequest r, HttpServletRequest req) {
        Account who = me();
        Account a = accounts.findById(id).orElse(null);
        if (a == null || !manages(who, a)) return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        a.active = r.active();
        accounts.save(a);
        audit.log(who.login, r.active() ? "ACCOUNT_ACTIVATED" : "ACCOUNT_SUSPENDED", a.login, req.getRemoteAddr());
        return ResponseEntity.ok(MaintenanceController.AccountView.of(a));
    }

    @PostMapping("/accounts/{id}/reset-password")
    public ResponseEntity<?> reset(@PathVariable Long id, HttpServletRequest req) {
        Account who = me();
        Account a = accounts.findById(id).orElse(null);
        if (a == null || !manages(who, a)) return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        String temp = temporaryPassword();
        a.passwordHash = encoder.encode(temp);
        a.mustChangePassword = true;
        a.failedAttempts = 0;
        a.lockedUntil = null;
        accounts.save(a);
        audit.log(who.login, "PASSWORD_RESET", a.login, req.getRemoteAddr());
        return ResponseEntity.ok(Map.of("account", MaintenanceController.AccountView.of(a), "temporaryPassword", temp));
    }
}

package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.management.ManagementFactory;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.AuditLog;
import ml.motosur.domain.AuditRepository;
import ml.motosur.domain.Moto;
import ml.motosur.domain.MotoRepository;
import ml.motosur.domain.Role;
import ml.motosur.domain.TheftRepository;
import ml.motosur.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/** Page et API de maintenance : réservées au rôle MAINTENANCE (voir SecurityConfig). */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern MATRICULE = Pattern.compile("^[A-Za-z0-9._-]{3,40}$");
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record AccountView(Long id, String login, String name, Role role, String commissariat, boolean active,
                              boolean mustChangePassword, Instant lockedUntil, Instant lastLoginAt, Instant createdAt) {
        static AccountView of(Account a) {
            return new AccountView(a.id, a.login, a.name, a.role, a.commissariat, a.active,
                    a.mustChangePassword, a.lockedUntil, a.lastLoginAt, a.createdAt);
        }
    }

    public record CreateAccount(@NotBlank @Size(max = 120) String login, @NotBlank @Size(max = 150) String name,
                                @NotNull Role role, @Size(max = 150) String commissariat) {}

    public record ActiveRequest(boolean active) {}

    public record AuditView(Instant at, String actor, String action, String detail, String ip) {
        static AuditView of(AuditLog l) {
            return new AuditView(l.at, l.actor, l.action, l.detail, l.ip);
        }
    }

    private final AccountRepository accounts;
    private final AuditRepository auditRepo;
    private final AuditService audit;
    private final MotoRepository motos;
    private final TheftRepository thefts;
    private final PasswordEncoder encoder;
    private final DataSource dataSource;
    private final String version;

    public MaintenanceController(AccountRepository accounts, AuditRepository auditRepo, AuditService audit,
                                 MotoRepository motos, TheftRepository thefts, PasswordEncoder encoder,
                                 DataSource dataSource, @Value("${motosur.version:0.1.0}") String version) {
        this.accounts = accounts;
        this.auditRepo = auditRepo;
        this.audit = audit;
        this.motos = motos;
        this.thefts = thefts;
        this.encoder = encoder;
        this.dataSource = dataSource;
        this.version = version;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Runtime rt = Runtime.getRuntime();
        Instant started = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("version", version);
        app.put("java", System.getProperty("java.version"));
        app.put("startedAt", started);
        app.put("uptimeSeconds", Duration.between(started, Instant.now()).getSeconds());
        app.put("memoryUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1_048_576);
        app.put("memoryMaxMb", rt.maxMemory() / 1_048_576);

        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            db.put("ok", c.isValid(2));
            db.put("product", c.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            db.put("ok", false);
            db.put("product", "inaccessible");
        }

        Map<String, Long> roles = new LinkedHashMap<>();
        for (Role r : Role.values()) {
            roles.put(r.name(), accounts.countByRole(r));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accounts", roles);
        data.put("motos", motos.count());
        data.put("thefts", thefts.count());
        data.put("motosStolen", motos.countByStatus(Moto.Status.STOLEN));
        data.put("motosRecovered", motos.countByStatus(Moto.Status.RECOVERED));

        Instant since = Instant.now().minus(Duration.ofHours(24));
        Map<String, Object> security = new LinkedHashMap<>();
        security.put("failedLogins24h", auditRepo.countByActionAndAtAfter("LOGIN_FAILED", since));
        security.put("lockouts24h", auditRepo.countByActionAndAtAfter("ACCOUNT_LOCKED", since));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("app", app);
        out.put("database", db);
        out.put("data", data);
        out.put("security", security);
        return out;
    }

    @GetMapping("/accounts")
    public List<AccountView> listAccounts() {
        return accounts.findAll().stream().map(AccountView::of).toList();
    }

    @PostMapping("/accounts")
    public ResponseEntity<?> createAccount(@Valid @RequestBody CreateAccount r, HttpServletRequest req) {
        if (r.role() == Role.MAINTENANCE) {
            return ResponseEntity.badRequest().body(Map.of("error", "ROLE_NOT_ALLOWED"));
        }
        String login = r.login().trim();
        boolean needsCommissariat = r.role() == Role.COMMISSARIAT || r.role() == Role.AGENT;
        if (r.role() == Role.AGENT) {
            if (!MATRICULE.matcher(login).matches()) {
                return ResponseEntity.badRequest().body(Map.of("error", "INVALID_LOGIN"));
            }
            login = login.toUpperCase();
        } else {
            if (!EMAIL.matcher(login).matches()) {
                return ResponseEntity.badRequest().body(Map.of("error", "INVALID_LOGIN"));
            }
            login = login.toLowerCase();
        }
        String commissariat = r.commissariat() == null ? "" : r.commissariat().trim();
        if (needsCommissariat && commissariat.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "COMMISSARIAT_REQUIRED"));
        }
        if (accounts.existsByLoginIgnoreCase(login)) {
            return ResponseEntity.status(409).body(Map.of("error", "ALREADY_EXISTS"));
        }
        String temp = temporaryPassword();
        Account a = new Account();
        a.login = login;
        a.name = r.name().trim();
        a.role = r.role();
        a.commissariat = needsCommissariat ? commissariat : null;
        a.passwordHash = encoder.encode(temp);
        a.mustChangePassword = true;
        accounts.save(a);
        audit.log(actor(), "ACCOUNT_CREATED", a.role + " " + a.login, req.getRemoteAddr());
        return ResponseEntity.status(201).body(Map.of("account", AccountView.of(a), "temporaryPassword", temp));
    }

    @PostMapping("/accounts/{id}/active")
    public ResponseEntity<?> setActive(@PathVariable Long id, @RequestBody ActiveRequest r, HttpServletRequest req) {
        Account a = accounts.findById(id).orElse(null);
        if (a == null) {
            return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        }
        if (a.role == Role.MAINTENANCE) {
            return ResponseEntity.badRequest().body(Map.of("error", "ROLE_NOT_ALLOWED"));
        }
        a.active = r.active();
        accounts.save(a);
        audit.log(actor(), r.active() ? "ACCOUNT_ACTIVATED" : "ACCOUNT_SUSPENDED", a.login, req.getRemoteAddr());
        return ResponseEntity.ok(AccountView.of(a));
    }

    @PostMapping("/accounts/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, HttpServletRequest req) {
        Account a = accounts.findById(id).orElse(null);
        if (a == null) {
            return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        }
        if (a.role == Role.MAINTENANCE) {
            return ResponseEntity.badRequest().body(Map.of("error", "ROLE_NOT_ALLOWED"));
        }
        String temp = temporaryPassword();
        a.passwordHash = encoder.encode(temp);
        a.mustChangePassword = true;
        a.failedAttempts = 0;
        a.lockedUntil = null;
        accounts.save(a);
        audit.log(actor(), "PASSWORD_RESET", a.login, req.getRemoteAddr());
        return ResponseEntity.ok(Map.of("account", AccountView.of(a), "temporaryPassword", temp));
    }

    @GetMapping("/audit")
    public List<AuditView> auditLog(@RequestParam(defaultValue = "50") int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return auditRepo.findAllByOrderByAtDesc(PageRequest.of(0, size)).stream().map(AuditView::of).toList();
    }

    private static String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private static String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}

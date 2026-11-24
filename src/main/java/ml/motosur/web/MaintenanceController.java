package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.AuditLog;
import ml.motosur.domain.AuditRepository;
import ml.motosur.domain.Moto;
import ml.motosur.domain.MotoRepository;
import ml.motosur.domain.Role;
import ml.motosur.domain.TheftRepository;
import ml.motosur.service.AccountService;
import ml.motosur.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Page et API de maintenance : réservées au rôle MAINTENANCE (voir SecurityConfig). */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    public record AccountView(Long id, String login, String name, Role role, String commissariat, String section,
                              String phone, boolean pending, boolean active, Instant lockedUntil, Instant lastLoginAt,
                              Instant createdAt) {
        static AccountView of(Account a) {
            return new AccountView(a.id, a.login, a.name, a.role, a.commissariat, a.section, a.phone, a.pending,
                    a.active, a.lockedUntil, a.lastLoginAt, a.createdAt);
        }
    }
    public record CreateAccount(@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 30) String phone,
                                @NotNull Role role, @Size(max = 150) String commissariat, @Size(max = 80) String section) {}
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
    private final AccountService accountService;
    private final DataSource dataSource;
    private final String version;

    public MaintenanceController(AccountRepository accounts, AuditRepository auditRepo, AuditService audit,
                                 MotoRepository motos, TheftRepository thefts, AccountService accountService,
                                 DataSource dataSource, @Value("${motosur.version:0.1.0}") String version) {
        this.accounts = accounts;
        this.auditRepo = auditRepo;
        this.audit = audit;
        this.motos = motos;
        this.thefts = thefts;
        this.accountService = accountService;
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

    /** Crée un compte et renvoie son lien d'invitation (valable 48 h, utilisable une seule fois). */
    @PostMapping("/accounts")
    public ResponseEntity<?> createAccount(@Valid @RequestBody CreateAccount r, HttpServletRequest req) {
        AccountService.Created c = accountService.create(actor(), r.name(), r.phone(), r.role(), r.commissariat(),
                r.section(), AccountService.baseUrl(req), req.getRemoteAddr());
        return ResponseEntity.status(201).body(AdminController.view(c));
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

    /** Nouveau lien d'invitation : l'ancien mot de passe et l'ancien lien cessent de fonctionner. */
    @PostMapping("/accounts/{id}/invite")
    public ResponseEntity<?> reinvite(@PathVariable Long id, HttpServletRequest req) {
        Account a = accounts.findById(id).orElse(null);
        if (a == null) {
            return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        }
        if (a.login.equalsIgnoreCase(actor())) {
            return ResponseEntity.badRequest().body(Map.of("error", "ROLE_NOT_ALLOWED"));
        }
        return ResponseEntity.ok(AdminController.view(
                accountService.reinvite(a, actor(), AccountService.baseUrl(req), req.getRemoteAddr())));
    }

    /**
     * Caisse du bureau (confidentielle : réservée à la maintenance) : totaux, détail par agent et par bureau,
     * et journal des paiements (nom, téléphone et montant payé par chaque propriétaire).
     */
    @GetMapping("/cash")
    @Transactional(readOnly = true)
    public Map<String, Object> cash(@RequestParam(defaultValue = "300") int limit) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Moto> paid = motos.findByPaidAmountNotNullOrderByRegisteredAtDesc();
        long totalAll = 0;
        long totalToday = 0;
        long countToday = 0;
        Map<String, long[]> byAgent = new LinkedHashMap<>();
        Map<String, long[]> bySection = new LinkedHashMap<>();
        for (Moto m : paid) {
            long amount = m.paidAmount == null ? 0 : m.paidAmount;
            boolean isToday = m.registeredAt.atZone(ZoneOffset.UTC).toLocalDate().equals(today);
            totalAll += amount;
            if (isToday) {
                totalToday += amount;
                countToday++;
            }
            long[] a = byAgent.computeIfAbsent(m.registeredByName == null ? "—" : m.registeredByName, k -> new long[3]);
            long[] b = bySection.computeIfAbsent(m.section == null ? "—" : m.section, k -> new long[3]);
            a[0]++;
            a[1] += amount;
            b[0]++;
            b[1] += amount;
            if (isToday) {
                a[2]++;
                b[2]++;
            }
        }
        List<Map<String, Object>> agents = new ArrayList<>();
        byAgent.forEach((k, v) -> agents.add(group(k, v)));
        List<Map<String, Object>> sections = new ArrayList<>();
        bySection.forEach((k, v) -> sections.add(group(k, v)));
        int size = Math.max(1, Math.min(limit, 1000));
        List<Map<String, Object>> journal = new ArrayList<>();
        for (Moto m : paid.stream().limit(size).toList()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("at", m.registeredAt);
            o.put("receipt", m.receiptNo);
            o.put("reference", m.reference);
            o.put("plate", m.plate);
            o.put("ownerName", m.ownerName);
            o.put("ownerPhone", m.ownerPhone);
            o.put("amount", m.paidAmount);
            o.put("mode", m.paymentMode);
            o.put("agent", m.registeredByName);
            o.put("checkedBy", m.checkedBy);
            o.put("section", m.section);
            journal.add(o);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("todayCount", countToday);
        out.put("todayTotal", totalToday);
        out.put("allCount", paid.size());
        out.put("allTotal", totalAll);
        out.put("byAgent", agents);
        out.put("bySection", sections);
        out.put("journal", journal);
        return out;
    }

    private static Map<String, Object> group(String name, long[] v) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("name", name);
        o.put("count", v[0]);
        o.put("total", v[1]);
        o.put("today", v[2]);
        return o;
    }

    @GetMapping("/audit")
    public List<AuditView> auditLog(@RequestParam(defaultValue = "50") int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return auditRepo.findAllByOrderByAtDesc(PageRequest.of(0, size)).stream().map(AuditView::of).toList();
    }

    private static String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

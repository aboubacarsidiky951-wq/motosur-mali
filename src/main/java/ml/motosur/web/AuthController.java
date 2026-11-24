package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.Role;
import ml.motosur.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final int MAX_ATTEMPTS = 5;
    static final long LOCK_MINUTES = 15;
    static final int MIN_PASSWORD = 10;

    public record LoginRequest(@NotBlank @Size(max = 120) String login, @NotBlank @Size(max = 200) String password) {}

    public record PasswordRequest(@NotBlank @Size(max = 200) String current, @NotBlank @Size(max = 200) String next) {}

    public record Me(String login, String name, Role role, String commissariat, String section, boolean mustChangePassword) {
        static Me of(Account a) {
            return new Me(a.login, a.name, a.role, a.commissariat, a.section, a.mustChangePassword);
        }
    }

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository contextRepository;
    private final AuditService audit;
    private final String dummyHash;
    private final RateLimiter loginLimit = new RateLimiter(20, 600_000);

    public AuthController(AccountRepository accounts, PasswordEncoder encoder,
                          SecurityContextRepository contextRepository, AuditService audit) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.contextRepository = contextRepository;
        this.audit = audit;
        this.dummyHash = encoder.encode("mot-de-passe-factice");
    }

    /** Appelé au chargement d'une page pour recevoir le cookie anti-CSRF. */
    @GetMapping("/csrf")
    public Map<String, Boolean> csrf() {
        return Map.of("ok", true);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest r, HttpServletRequest req,
                                   HttpServletResponse res) {
        String ip = req.getRemoteAddr();
        if (!loginLimit.allow(ip)) {
            return ResponseEntity.status(429).body(Map.of("error", "TOO_MANY_REQUESTS"));
        }
        String login = normalizeLogin(r.login());
        Account a = accounts.findByLoginIgnoreCase(login).orElse(null);
        Instant now = Instant.now();

        if (a == null) {
            encoder.matches(r.password(), dummyHash); // même durée de réponse qu'un vrai compte
            audit.log(login, "LOGIN_FAILED", "Identifiant inconnu", ip);
            return ResponseEntity.status(401).body(Map.of("error", "INVALID_CREDENTIALS"));
        }
        if (a.lockedUntil != null && a.lockedUntil.isAfter(now)) {
            audit.log(a.login, "LOGIN_BLOCKED", "Compte verrouillé", ip);
            return ResponseEntity.status(423).body(Map.of("error", "LOCKED"));
        }
        if (!encoder.matches(r.password(), a.passwordHash)) {
            a.failedAttempts++;
            if (a.failedAttempts >= MAX_ATTEMPTS) {
                a.lockedUntil = now.plusSeconds(LOCK_MINUTES * 60);
                a.failedAttempts = 0;
                audit.log(a.login, "ACCOUNT_LOCKED", "Trop de tentatives : verrouillé " + LOCK_MINUTES + " min", ip);
            }
            accounts.save(a);
            audit.log(a.login, "LOGIN_FAILED", "Mot de passe incorrect", ip);
            return ResponseEntity.status(401).body(Map.of("error", "INVALID_CREDENTIALS"));
        }
        if (!a.active) {
            audit.log(a.login, "LOGIN_BLOCKED", "Compte suspendu", ip);
            return ResponseEntity.status(403).body(Map.of("error", "SUSPENDED"));
        }

        a.failedAttempts = 0;
        a.lockedUntil = null;
        a.lastLoginAt = now;
        accounts.save(a);

        HttpSession existing = req.getSession(false);
        if (existing != null) {
            req.changeSessionId();
        } else {
            req.getSession(true);
        }
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                a.login, null, List.of(new SimpleGrantedAuthority("ROLE_" + a.role.name())));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepository.saveContext(ctx, req, res);

        audit.log(a.login, "LOGIN_OK", null, ip);
        return ResponseEntity.ok(Me.of(a));
    }

    /** Un numéro de téléphone (avec ou sans +223) est ramené à ses 8 chiffres ; un email reste tel quel. */
    static String normalizeLogin(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.contains("@")) return t;
        String p = ml.motosur.service.AccountService.phone8(t);
        return p != null ? p : t;
    }

    @GetMapping("/me")
    public Me me() {
        return Me.of(current());
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            audit.log(auth.getName(), "LOGOUT", null, req.getRemoteAddr());
        }
        SecurityContextHolder.clearContext();
        HttpSession s = req.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        return Map.of("ok", true);
    }

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody PasswordRequest r, HttpServletRequest req) {
        Account a = current();
        if (!encoder.matches(r.current(), a.passwordHash)) {
            audit.log(a.login, "PASSWORD_FAILED", "Ancien mot de passe incorrect", req.getRemoteAddr());
            return ResponseEntity.status(400).body(Map.of("error", "WRONG_CURRENT_PASSWORD"));
        }
        String next = r.next();
        if (next.length() < MIN_PASSWORD || next.equalsIgnoreCase(a.login) || next.equals(r.current())) {
            return ResponseEntity.status(400).body(Map.of("error", "WEAK_PASSWORD"));
        }
        a.passwordHash = encoder.encode(next);
        a.mustChangePassword = false;
        accounts.save(a);
        audit.log(a.login, "PASSWORD_CHANGED", null, req.getRemoteAddr());
        return ResponseEntity.ok(Me.of(a));
    }

    private Account current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return accounts.findByLoginIgnoreCase(auth.getName()).orElseThrow();
    }
}

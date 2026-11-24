package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.Invitation;
import ml.motosur.domain.InvitationRepository;
import ml.motosur.service.AccountService;
import ml.motosur.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ouverture d'un compte à partir d'un lien d'invitation (sans connexion préalable). */
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {
    public record TokenRequest(@NotBlank @Size(max = 200) String token) {}
    public record AcceptRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 200) String password) {}

    private final InvitationRepository invitations;
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository contextRepository;
    private final AuditService audit;
    private final RateLimiter limit = new RateLimiter(30, 600_000);

    public InvitationController(InvitationRepository invitations, AccountRepository accounts, PasswordEncoder encoder,
                                SecurityContextRepository contextRepository, AuditService audit) {
        this.invitations = invitations;
        this.accounts = accounts;
        this.encoder = encoder;
        this.contextRepository = contextRepository;
        this.audit = audit;
    }

    private static boolean usable(Invitation i) {
        return i != null && i.usedAt == null && i.expiresAt.isAfter(Instant.now());
    }

    @PostMapping("/check")
    @Transactional(readOnly = true)
    public ResponseEntity<?> check(@Valid @RequestBody TokenRequest r, HttpServletRequest req) {
        if (!limit.allow(req.getRemoteAddr())) throw new ApiException(429, "TOO_MANY_REQUESTS");
        Invitation inv = invitations.findByTokenHash(AccountService.sha256(r.token().trim())).orElse(null);
        if (!usable(inv)) throw new ApiException(410, "INVALID_LINK");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", inv.account.name);
        out.put("role", inv.account.role.name());
        out.put("section", inv.account.section);
        out.put("commissariat", inv.account.commissariat);
        out.put("expiresAt", inv.expiresAt);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/accept")
    @Transactional
    public ResponseEntity<?> accept(@Valid @RequestBody AcceptRequest r, HttpServletRequest req, HttpServletResponse res) {
        String ip = req.getRemoteAddr();
        if (!limit.allow(ip)) throw new ApiException(429, "TOO_MANY_REQUESTS");
        Invitation inv = invitations.findByTokenHash(AccountService.sha256(r.token().trim())).orElse(null);
        if (!usable(inv)) throw new ApiException(410, "INVALID_LINK");
        Account a = inv.account;
        String pw = r.password();
        if (pw.length() < AuthController.MIN_PASSWORD || pw.equalsIgnoreCase(a.login) || pw.equalsIgnoreCase(a.name)
                || pw.chars().allMatch(Character::isDigit)) {
            throw new ApiException(400, "WEAK_PASSWORD");
        }
        Instant now = Instant.now();
        a.passwordHash = encoder.encode(pw);
        a.pending = false;
        a.mustChangePassword = false;
        a.failedAttempts = 0;
        a.lockedUntil = null;
        a.lastLoginAt = now;
        accounts.save(a);
        inv.usedAt = now; // le lien ne sert qu'une fois
        invitations.save(inv);

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
        audit.log(a.login, "ACCOUNT_OPENED", "Lien d'invitation utilisé (" + a.role + ")", ip);
        return ResponseEntity.ok(AuthController.Me.of(a));
    }
}

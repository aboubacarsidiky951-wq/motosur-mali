package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.service.AuditService;
import ml.motosur.service.TotpService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/** Double vérification des comptes de maintenance (voir SecurityConfig : /api/maintenance/** réservé à la maintenance). */
@RestController
@RequestMapping("/api/maintenance/2fa")
public class TotpController {

    public record CodeRequest(@NotBlank @Size(max = 12) String code) {}

    public record DisableRequest(@NotBlank @Size(max = 200) String password, @NotBlank @Size(max = 12) String code) {}

    private final AccountRepository accounts;
    private final TotpService totp;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public TotpController(AccountRepository accounts, TotpService totp, PasswordEncoder encoder, AuditService audit) {
        this.accounts = accounts;
        this.totp = totp;
        this.encoder = encoder;
        this.audit = audit;
    }

    private Account me() {
        String login = SecurityContextHolder.getContext().getAuthentication().getName();
        return accounts.findByLoginIgnoreCase(login).orElseThrow();
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("enabled", me().totpEnabled);
    }

    /** Étape 1 : génère une clé secrète à scanner dans l'application d'authentification. Rien n'est actif tant que le code n'a pas été confirmé. */
    @PostMapping("/setup")
    public Map<String, Object> setup(HttpServletRequest req) {
        Account a = me();
        if (a.totpEnabled) throw new ApiException(409, "ALREADY_ENABLED");
        a.totpSecret = totp.newSecret();
        accounts.save(a);
        audit.log(a.login, "TOTP_SETUP", null, req.getRemoteAddr());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("secret", a.totpSecret);
        out.put("uri", "otpauth://totp/" + enc("MotoSûr Mali:" + a.login) + "?secret=" + a.totpSecret
                + "&issuer=" + enc("MotoSûr Mali") + "&algorithm=SHA1&digits=6&period=30");
        return out;
    }

    /** Étape 2 : confirme avec un premier code, ce qui active la double vérification. */
    @PostMapping("/enable")
    public ResponseEntity<?> enable(@Valid @RequestBody CodeRequest r, HttpServletRequest req) {
        Account a = me();
        if (a.totpEnabled) throw new ApiException(409, "ALREADY_ENABLED");
        if (a.totpSecret == null) throw new ApiException(400, "NO_SETUP");
        long step = totp.verify(a.totpSecret, r.code(), a.lastTotpStep);
        if (step < 0) {
            audit.log(a.login, "TOTP_ENABLE_FAILED", "Code incorrect", req.getRemoteAddr());
            throw new ApiException(400, "INVALID_CODE");
        }
        a.totpEnabled = true;
        a.lastTotpStep = step;
        accounts.save(a);
        audit.log(a.login, "TOTP_ENABLED", null, req.getRemoteAddr());
        return ResponseEntity.ok(Map.of("enabled", true));
    }

    /** Désactivation : mot de passe et code actuel exigés. */
    @PostMapping("/disable")
    public ResponseEntity<?> disable(@Valid @RequestBody DisableRequest r, HttpServletRequest req) {
        Account a = me();
        if (!a.totpEnabled) throw new ApiException(400, "NOT_ENABLED");
        if (!encoder.matches(r.password(), a.passwordHash)) {
            audit.log(a.login, "TOTP_DISABLE_FAILED", "Mot de passe incorrect", req.getRemoteAddr());
            throw new ApiException(400, "WRONG_CURRENT_PASSWORD");
        }
        long step = totp.verify(a.totpSecret, r.code(), a.lastTotpStep);
        if (step < 0) {
            audit.log(a.login, "TOTP_DISABLE_FAILED", "Code incorrect", req.getRemoteAddr());
            throw new ApiException(400, "INVALID_CODE");
        }
        a.totpEnabled = false;
        a.totpSecret = null;
        a.lastTotpStep = 0;
        accounts.save(a);
        audit.log(a.login, "TOTP_DISABLED", null, req.getRemoteAddr());
        return ResponseEntity.ok(Map.of("enabled", false));
    }
}

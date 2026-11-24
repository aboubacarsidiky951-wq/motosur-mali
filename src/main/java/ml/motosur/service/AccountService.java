package ml.motosur.service;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.Invitation;
import ml.motosur.domain.InvitationRepository;
import ml.motosur.domain.Role;
import ml.motosur.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Création des comptes par lien d'invitation. Personne ne connaît jamais le mot de passe d'un autre :
 * le créateur reçoit un lien personnel, valable 48 heures et utilisable une seule fois.
 */
@Service
public class AccountService {
    public static final Duration VALIDITY = Duration.ofHours(48);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Created(Account account, String inviteUrl, Instant expiresAt) {}

    private final AccountRepository accounts;
    private final InvitationRepository invitations;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AccountService(AccountRepository accounts, InvitationRepository invitations, PasswordEncoder encoder,
                          AuditService audit) {
        this.accounts = accounts;
        this.invitations = invitations;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** 8 chiffres maliens, avec ou sans indicatif ; null si invalide. */
    public static String phone8(String s) {
        String d = s == null ? "" : s.replaceAll("\\D", "");
        if (d.startsWith("00223")) d = d.substring(5);
        else if (d.startsWith("223") && d.length() == 11) d = d.substring(3);
        return d.matches("\\d{8}") ? d : null;
    }

    public static String baseUrl(HttpServletRequest req) {
        return ServletUriComponentsBuilder.fromContextPath(req).build().toUriString();
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Mot de passe aléatoire que personne ne connaît : le compte reste fermé tant que le lien n'est pas utilisé. */
    private String unusablePassword() {
        return encoder.encode(randomToken());
    }

    @Transactional
    public Created create(String actor, String name, String phoneIn, Role role, String commissariat, String section,
                          String baseUrl, String ip) {
        String ph = phone8(phoneIn);
        if (ph == null) throw new ApiException(400, "INVALID_PHONE");
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.length() < 3) throw new ApiException(400, "INVALID_NAME");
        boolean needsCom = role == Role.COMMISSARIAT || role == Role.AGENT;
        boolean needsSection = role == Role.BUREAU_RESP || role == Role.BUREAU_AGENT;
        String com = commissariat == null ? "" : commissariat.trim();
        String sec = section == null ? "" : section.trim();
        if (needsCom && com.isEmpty()) throw new ApiException(400, "COMMISSARIAT_REQUIRED");
        if (needsSection && sec.isEmpty()) throw new ApiException(400, "SECTION_REQUIRED");
        if (accounts.existsByLoginIgnoreCase(ph)) throw new ApiException(409, "ALREADY_EXISTS");
        Account a = new Account();
        a.login = ph;
        a.phone = ph;
        a.name = cleanName;
        a.role = role;
        a.commissariat = needsCom ? com : null;
        a.section = needsSection ? sec : null;
        a.passwordHash = unusablePassword();
        a.pending = true;
        a.mustChangePassword = false;
        accounts.save(a);
        audit.log(actor, "ACCOUNT_CREATED", role + " " + cleanName + " (" + ph + ")", ip);
        return invite(a, actor, baseUrl);
    }

    /** Nouveau lien pour un compte existant (lien perdu, expiré ou mot de passe oublié). L'ancien lien est annulé. */
    @Transactional
    public Created reinvite(Account a, String actor, String baseUrl, String ip) {
        a.passwordHash = unusablePassword();
        a.pending = true;
        a.mustChangePassword = false;
        a.failedAttempts = 0;
        a.lockedUntil = null;
        accounts.save(a);
        audit.log(actor, "INVITE_RENEWED", a.role + " " + a.login, ip);
        return invite(a, actor, baseUrl);
    }

    private Created invite(Account a, String actor, String baseUrl) {
        Instant now = Instant.now();
        for (Invitation old : invitations.findByAccountAndUsedAtIsNull(a)) {
            old.usedAt = now;
            invitations.save(old);
        }
        String token = randomToken();
        Invitation inv = new Invitation();
        inv.account = a;
        inv.tokenHash = sha256(token);
        inv.expiresAt = now.plus(VALIDITY);
        inv.createdBy = actor;
        invitations.save(inv);
        return new Created(a, baseUrl + "/invitation.html#" + token, inv.expiresAt);
    }
}

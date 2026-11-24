package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.Role;
import ml.motosur.service.AccountService;
import ml.motosur.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Comptes créés par la hiérarchie, toujours par lien d'invitation :
 * la Direction générale crée les commissariats (et des agents), un commissariat crée ses agents,
 * un responsable de bureau crée les agents de son bureau.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    public record CreateAccount(@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 30) String phone,
                                @NotNull Role role, @Size(max = 150) String commissariat) {}
    public record ActiveRequest(boolean active) {}

    private final AccountRepository accounts;
    private final AccountService accountService;
    private final AuditService audit;

    public AdminController(AccountRepository accounts, AccountService accountService, AuditService audit) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.audit = audit;
    }

    private Account me() {
        String login = SecurityContextHolder.getContext().getAuthentication().getName();
        return accounts.findByLoginIgnoreCase(login).orElseThrow();
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    /** Ce compte est-il sous la responsabilité de l'utilisateur connecté ? */
    private static boolean manages(Account who, Account target) {
        if (who.role == Role.CENTRAL) {
            return target.role == Role.COMMISSARIAT || target.role == Role.AGENT;
        }
        if (who.role == Role.COMMISSARIAT) {
            return target.role == Role.AGENT && same(who.commissariat, target.commissariat);
        }
        if (who.role == Role.BUREAU_RESP) {
            return target.role == Role.BUREAU_AGENT && same(who.section, target.section);
        }
        return false;
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
        String commissariat = null;
        String section = null;
        if (who.role == Role.CENTRAL) {
            if (role != Role.COMMISSARIAT && role != Role.AGENT) throw new ApiException(400, "ROLE_NOT_ALLOWED");
            commissariat = r.commissariat();
        } else if (who.role == Role.COMMISSARIAT) {
            if (role != Role.AGENT) throw new ApiException(400, "ROLE_NOT_ALLOWED");
            commissariat = who.commissariat;
        } else if (who.role == Role.BUREAU_RESP) {
            if (role != Role.BUREAU_AGENT) throw new ApiException(400, "ROLE_NOT_ALLOWED");
            section = who.section;
        } else {
            throw new ApiException(403, "FORBIDDEN");
        }
        AccountService.Created c = accountService.create(who.login, r.name(), r.phone(), role, commissariat, section,
                AccountService.baseUrl(req), req.getRemoteAddr());
        return ResponseEntity.status(201).body(view(c));
    }

    @PostMapping("/accounts/{id}/active")
    public ResponseEntity<?> setActive(@PathVariable Long id, @RequestBody ActiveRequest r, HttpServletRequest req) {
        Account who = me();
        Account a = accounts.findById(id).orElse(null);
        if (a == null || !manages(who, a)) throw new ApiException(404, "NOT_FOUND");
        a.active = r.active();
        accounts.save(a);
        audit.log(who.login, r.active() ? "ACCOUNT_ACTIVATED" : "ACCOUNT_SUSPENDED", a.login, req.getRemoteAddr());
        return ResponseEntity.ok(MaintenanceController.AccountView.of(a));
    }

    /** Nouveau lien d'invitation (lien perdu ou expiré, mot de passe oublié). */
    @PostMapping("/accounts/{id}/invite")
    public ResponseEntity<?> reinvite(@PathVariable Long id, HttpServletRequest req) {
        Account who = me();
        Account a = accounts.findById(id).orElse(null);
        if (a == null || !manages(who, a)) throw new ApiException(404, "NOT_FOUND");
        return ResponseEntity.ok(view(accountService.reinvite(a, who.login, AccountService.baseUrl(req), req.getRemoteAddr())));
    }

    static Map<String, Object> view(AccountService.Created c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("account", MaintenanceController.AccountView.of(c.account()));
        out.put("inviteUrl", c.inviteUrl());
        out.put("expiresAt", c.expiresAt());
        return out;
    }
}

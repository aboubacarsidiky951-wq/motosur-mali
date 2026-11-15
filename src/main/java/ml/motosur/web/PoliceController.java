package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.Moto;
import ml.motosur.domain.MotoRepository;
import ml.motosur.domain.Role;
import ml.motosur.domain.Theft;
import ml.motosur.domain.TheftRepository;
import ml.motosur.service.AuditService;
import ml.motosur.service.SmsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Espace police : commissariats, agents et Direction générale (voir SecurityConfig). */
@RestController
@RequestMapping("/api/police")
public class PoliceController {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record RecoveredRequest(@NotBlank @Size(max = 150) String commissariat, @NotBlank @Size(max = 40) String commune) {}

    private final MotoRepository motos;
    private final TheftRepository thefts;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final SmsService smsService;

    public PoliceController(MotoRepository motos, TheftRepository thefts, AccountRepository accounts, AuditService audit,
                            SmsService smsService) {
        this.smsService = smsService;
        this.motos = motos;
        this.thefts = thefts;
        this.accounts = accounts;
        this.audit = audit;
    }

    private Account me() {
        String login = SecurityContextHolder.getContext().getAuthentication().getName();
        return accounts.findByLoginIgnoreCase(login).orElseThrow();
    }

    private static Map<String, Object> alertView(Theft t) {
        Moto m = t.moto;
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("motoId", m.id);
        o.put("reference", t.reference);
        o.put("plate", m.plate);
        o.put("brand", m.brand);
        o.put("model", m.model);
        o.put("color", m.color);
        o.put("theftDate", t.theftDate);
        o.put("theftCommune", t.commune);
        o.put("place", t.place);
        o.put("details", t.details);
        o.put("reportedAt", t.reportedAt);
        o.put("ownerName", m.ownerName);
        o.put("ownerPhone", m.ownerPhone);
        o.put("status", m.status.name());
        o.put("recoveredAt", t.recoveredAt);
        o.put("recoveredCommissariat", t.recoveredCommissariat);
        o.put("recoveredCommune", t.recoveredCommune);
        return o;
    }

    @GetMapping("/check")
    @Transactional
    public Map<String, Object> check(@RequestParam String q, HttpServletRequest req) {
        audit.log(me().login, "POLICE_CHECK", q.length() > 40 ? q.substring(0, 40) : q, req.getRemoteAddr());
        String n = PublicController.norm(q);
        Map<String, Object> out = new LinkedHashMap<>();
        Moto m = n.length() < 4 ? null : motos.findFirstByPlateOrChassisOrEngine(n, n, n).orElse(null);
        if (m == null) {
            out.put("found", false);
            return out;
        }
        out.put("found", true);
        out.put("motoId", m.id);
        out.put("reference", m.reference);
        out.put("status", m.status.name());
        out.put("plate", m.plate);
        out.put("brand", m.brand);
        out.put("model", m.model);
        out.put("color", m.color);
        out.put("year", m.year);
        out.put("chassis", m.chassis);
        out.put("engine", m.engine);
        out.put("ownerName", m.ownerName);
        out.put("ownerPhone", m.ownerPhone);
        out.put("commune", m.commune);
        out.put("registeredAt", m.registeredAt);
        Theft t = thefts.findFirstByMotoOrderByReportedAtDesc(m).orElse(null);
        if (t != null) {
            out.put("theft", alertView(t));
        }
        return out;
    }

    @GetMapping("/alerts")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> alerts(@RequestParam(defaultValue = "active") String status,
                                            @RequestParam(defaultValue = "") String q,
                                            @RequestParam(defaultValue = "") String commune) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Theft t : thefts.findAllWithMoto()) {
            boolean recovered = t.recoveredAt != null;
            if (status.equals("active") && recovered) continue;
            if (status.equals("recovered") && !recovered) continue;
            if (!commune.isBlank() && !commune.equals(t.commune)) continue;
            if (!needle.isEmpty()) {
                String hay = (t.moto.plate + " " + t.moto.brand + " " + t.moto.model + " " + t.moto.ownerName).toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) continue;
            }
            out.add(alertView(t));
        }
        return out;
    }

    @PostMapping("/motos/{id}/recovered")
    @Transactional
    public ResponseEntity<?> recovered(@PathVariable Long id, @Valid @RequestBody RecoveredRequest r, HttpServletRequest req) {
        if (!PublicController.COMMUNES.contains(r.commune())) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_COMMUNE"));
        }
        Moto m = motos.findById(id).orElse(null);
        if (m == null) return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        if (m.status != Moto.Status.STOLEN) return ResponseEntity.status(409).body(Map.of("error", "NOT_STOLEN"));
        Account a = me();
        // Un commissariat ou un agent ne peut remettre une moto qu'à son propre commissariat.
        String commissariat = (a.role == Role.CENTRAL || a.commissariat == null) ? r.commissariat().trim() : a.commissariat;
        Theft t = thefts.findFirstByMotoOrderByReportedAtDesc(m).orElseThrow();
        t.recoveredAt = Instant.now();
        t.recoveredReference = "RET-" + LocalDate.now().getYear() + "-" + String.format("%06d", RANDOM.nextInt(1_000_000));
        t.recoveredCommissariat = commissariat;
        t.recoveredCommune = r.commune();
        thefts.save(t);
        m.status = Moto.Status.RECOVERED;
        motos.save(m);

        String sms = "MotoSûr : votre moto " + m.plate + " a été retrouvée. Elle se trouve au " + commissariat
                + ". Présentez-vous avec vos papiers. Réf. " + t.recoveredReference;
        String last4 = m.ownerPhone.substring(m.ownerPhone.length() - 4);
        audit.log(a.login, "MOTO_RECOVERED", t.recoveredReference + " " + m.plate + " → " + commissariat, req.getRemoteAddr());
        boolean sent = smsService.send(m.ownerPhone, sms);
        audit.log("système", sent ? "SMS_ENVOYE" : "SMS_NON_ENVOYE",
                "À ****" + last4 + " : " + sms + (sent ? "" : smsService.enabled() ? " (refusé par le fournisseur)" : " (fournisseur SMS non configuré)"), null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reference", t.recoveredReference);
        out.put("plate", m.plate);
        out.put("commissariat", commissariat);
        out.put("sms", sms);
        out.put("smsSent", sent);
        return ResponseEntity.ok(out);
    }
}

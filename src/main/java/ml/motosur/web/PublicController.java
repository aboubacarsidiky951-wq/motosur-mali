package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ml.motosur.domain.Moto;
import ml.motosur.domain.MotoRepository;
import ml.motosur.domain.Notification;
import ml.motosur.domain.NotificationRepository;
import ml.motosur.domain.Theft;
import ml.motosur.domain.TheftRepository;
import ml.motosur.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** API publique (sans connexion) : enregistrer, vérifier, déclarer un vol, lever l'alerte. */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    static final List<String> COMMUNES = List.of("Commune I", "Commune II", "Commune III", "Commune IV", "Commune V", "Commune VI");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String ownerName,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @Size(max = 40) String commune,
            @NotBlank @Size(max = 30) String plate,
            @NotBlank @Size(max = 40) String chassis,
            @NotBlank @Size(max = 40) String engine,
            @NotBlank @Size(max = 60) String brand,
            @NotBlank @Size(max = 60) String model,
            @NotBlank @Size(max = 30) String color,
            @NotNull @Min(1990) @Max(2100) Integer year) {}

    public record TheftRequest(
            @NotBlank @Size(max = 60) String q,
            @NotBlank @Size(max = 30) String phone,
            @NotNull LocalDate date,
            @NotBlank @Size(max = 40) String commune,
            @NotBlank @Size(max = 200) String place,
            @Size(max = 1000) String details) {}

    public record RecoveredRequest(@NotBlank @Size(max = 60) String q, @NotBlank @Size(max = 30) String phone) {}

    private final MotoRepository motos;
    private final TheftRepository thefts;
    private final NotificationRepository notifications;
    private final AuditService audit;
    private final RateLimiter notifLimit = new RateLimiter(30, 600_000);
    private final RateLimiter checkLimit = new RateLimiter(40, 60_000);
    private final RateLimiter writeLimit = new RateLimiter(10, 600_000);

    public PublicController(MotoRepository motos, TheftRepository thefts, NotificationRepository notifications,
                            AuditService audit) {
        this.notifications = notifications;
        this.motos = motos;
        this.thefts = thefts;
        this.audit = audit;
    }

    static String norm(String s) {
        return s == null ? "" : s.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /** 8 chiffres maliens, avec ou sans indicatif ; null si invalide. */
    static String phone(String s) {
        String d = s == null ? "" : s.replaceAll("\\D", "");
        if (d.startsWith("00223")) d = d.substring(5);
        else if (d.startsWith("223") && d.length() == 11) d = d.substring(3);
        return d.matches("\\d{8}") ? d : null;
    }

    private static String mask(String s) {
        return s.length() <= 4 ? "••••" : "•".repeat(s.length() - 4) + s.substring(s.length() - 4);
    }

    private static String ref(String prefix) {
        return prefix + "-" + LocalDate.now().getYear() + "-" + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static ResponseEntity<Map<String, String>> err(int status, String code) {
        return ResponseEntity.status(status).body(Map.of("error", code));
    }

    @GetMapping("/communes")
    public List<String> communes() {
        return COMMUNES;
    }

    @PostMapping("/motos")
    @Transactional
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest r, HttpServletRequest req) {
        if (!writeLimit.allow(req.getRemoteAddr())) return err(429, "TOO_MANY_REQUESTS");
        String ph = phone(r.phone());
        if (ph == null) return err(400, "INVALID_PHONE");
        if (!COMMUNES.contains(r.commune())) return err(400, "INVALID_COMMUNE");
        int maxYear = LocalDate.now().getYear() + 1;
        if (r.year() > maxYear) return err(400, "INVALID_YEAR");
        String plate = norm(r.plate()), chassis = norm(r.chassis()), engine = norm(r.engine());
        if (plate.length() < 4 || chassis.length() < 5 || engine.length() < 4) return err(400, "INVALID_NUMBERS");
        if (motos.existsByPlate(plate)) return err(409, "PLATE_EXISTS");
        if (motos.existsByChassis(chassis)) return err(409, "CHASSIS_EXISTS");
        if (motos.existsByEngine(engine)) return err(409, "ENGINE_EXISTS");

        Moto m = new Moto();
        m.reference = ref("MS");
        m.plate = plate;
        m.chassis = chassis;
        m.engine = engine;
        m.brand = r.brand().trim();
        m.model = r.model().trim();
        m.color = r.color().trim();
        m.year = r.year();
        m.ownerName = r.ownerName().trim();
        m.ownerPhone = ph;
        m.commune = r.commune();
        motos.save(m);
        audit.log("public", "MOTO_REGISTERED", m.reference + " " + m.plate, req.getRemoteAddr());
        return ResponseEntity.status(201).body(Map.of("reference", m.reference, "plate", m.plate));
    }

    @GetMapping("/check")
    public ResponseEntity<?> check(@RequestParam String q, HttpServletRequest req) {
        if (!checkLimit.allow(req.getRemoteAddr())) return err(429, "TOO_MANY_REQUESTS");
        String n = norm(q);
        Map<String, Object> out = new LinkedHashMap<>();
        if (n.length() < 4 || n.length() > 40) {
            out.put("found", false);
            return ResponseEntity.ok(out);
        }
        Moto m = motos.findFirstByPlateOrChassisOrEngine(n, n, n).orElse(null);
        if (m == null) {
            out.put("found", false);
            return ResponseEntity.ok(out);
        }
        out.put("found", true);
        out.put("status", m.status.name());
        out.put("plate", m.plate);
        out.put("brand", m.brand);
        out.put("model", m.model);
        out.put("color", m.color);
        out.put("year", m.year);
        out.put("chassis", mask(m.chassis));
        out.put("engine", mask(m.engine));
        Theft t = thefts.findFirstByMotoOrderByReportedAtDesc(m).orElse(null);
        if (t != null && m.status == Moto.Status.STOLEN) {
            out.put("theftDate", t.theftDate);
            out.put("theftCommune", t.commune);
        }
        if (t != null && m.status == Moto.Status.RECOVERED) {
            out.put("recoveredAt", t.recoveredAt);
        }
        return ResponseEntity.ok(out);
    }

    /** Trouve la moto d'un propriétaire : même réponse pour « inconnue » et « mauvais téléphone ». */
    private Moto owned(String q, String phoneIn) {
        String ph = phone(phoneIn);
        String n = norm(q);
        if (ph == null || n.length() < 4) return null;
        Moto m = motos.findFirstByPlateOrChassisOrEngine(n, n, n).orElse(null);
        return m != null && m.ownerPhone.equals(ph) ? m : null;
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String q, HttpServletRequest req) {
        // Étape 1 de « Déclarer un vol » : confirme l'existence de la moto, sans données personnelles.
        return check(q, req);
    }

    @PostMapping("/thefts")
    @Transactional
    public ResponseEntity<?> declareTheft(@Valid @RequestBody TheftRequest r, HttpServletRequest req) {
        if (!writeLimit.allow(req.getRemoteAddr())) return err(429, "TOO_MANY_REQUESTS");
        if (!COMMUNES.contains(r.commune())) return err(400, "INVALID_COMMUNE");
        if (r.date().isAfter(LocalDate.now()) || r.date().isBefore(LocalDate.now().minusYears(1))) {
            return err(400, "INVALID_DATE");
        }
        Moto m = owned(r.q(), r.phone());
        if (m == null) {
            audit.log("public", "THEFT_REFUSED", "Moto ou téléphone non reconnus", req.getRemoteAddr());
            return err(403, "NOT_OWNER");
        }
        if (m.status == Moto.Status.STOLEN) return err(409, "ALREADY_STOLEN");
        Theft t = new Theft();
        t.moto = m;
        t.reference = ref("VOL");
        t.theftDate = r.date();
        t.commune = r.commune();
        t.place = r.place().trim();
        t.details = r.details() == null || r.details().isBlank() ? null : r.details().trim();
        thefts.save(t);
        m.status = Moto.Status.STOLEN;
        motos.save(m);
        audit.log("public", "THEFT_DECLARED", t.reference + " " + m.plate, req.getRemoteAddr());
        return ResponseEntity.status(201).body(Map.of("reference", t.reference, "plate", m.plate));
    }

    @PostMapping("/recovered")
    @Transactional
    public ResponseEntity<?> recovered(@Valid @RequestBody RecoveredRequest r, HttpServletRequest req) {
        if (!writeLimit.allow(req.getRemoteAddr())) return err(429, "TOO_MANY_REQUESTS");
        Moto m = owned(r.q(), r.phone());
        if (m == null) {
            audit.log("public", "RECOVERY_REFUSED", "Moto ou téléphone non reconnus", req.getRemoteAddr());
            return err(403, "NOT_OWNER");
        }
        if (m.status != Moto.Status.STOLEN) return err(409, "NOT_STOLEN");
        Theft t = thefts.findFirstByMotoOrderByReportedAtDesc(m).orElseThrow();
        t.recoveredAt = Instant.now();
        t.recoveredReference = ref("RET");
        thefts.save(t);
        m.status = Moto.Status.RECOVERED;
        motos.save(m);
        audit.log("public", "MOTO_RECOVERED", t.recoveredReference + " " + m.plate, req.getRemoteAddr());
        return ResponseEntity.ok(Map.of("reference", t.recoveredReference, "plate", m.plate));
    }

    /** Cloche de notification : le propriétaire (plaque + téléphone) lit les messages concernant sa moto. */
    @PostMapping("/notifications")
    @Transactional
    public ResponseEntity<?> myNotifications(@Valid @RequestBody RecoveredRequest r, HttpServletRequest req) {
        if (!notifLimit.allow(req.getRemoteAddr())) return err(429, "TOO_MANY_REQUESTS");
        Moto m = owned(r.q(), r.phone());
        if (m == null) return err(403, "NOT_OWNER");
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        long unread = 0;
        for (Notification n : notifications.findTop20ByMotoOrderByCreatedAtDesc(m)) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", n.id);
            o.put("message", n.message);
            o.put("createdAt", n.createdAt);
            o.put("seen", n.seen);
            if (!n.seen) unread++;
            items.add(o);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plate", m.plate);
        out.put("unread", unread);
        out.put("items", items);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/notifications/read")
    @Transactional
    public ResponseEntity<?> markRead(@Valid @RequestBody RecoveredRequest r, HttpServletRequest req) {
        if (!notifLimit.allow(req.getRemoteAddr())) return err(429, "TOO_MANY_REQUESTS");
        Moto m = owned(r.q(), r.phone());
        if (m == null) return err(403, "NOT_OWNER");
        for (Notification n : notifications.findTop20ByMotoOrderByCreatedAtDesc(m)) {
            n.seen = true;
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
}

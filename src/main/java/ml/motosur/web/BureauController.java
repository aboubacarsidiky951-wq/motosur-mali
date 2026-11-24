package ml.motosur.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.Moto;
import ml.motosur.domain.MotoRepository;
import ml.motosur.domain.Theft;
import ml.motosur.domain.TheftRepository;
import ml.motosur.service.AccountService;
import ml.motosur.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Bureaux d'enregistrement : l'enregistrement d'une moto se fait uniquement ici, par un agent du bureau,
 * numéros relevés sur la moto et relus par un second agent. Le bureau déclare aussi les vols pour les propriétaires.
 */
@RestController
@RequestMapping("/api/bureau")
public class BureauController {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final List<String> MODES = List.of("Espèces", "Orange Money", "Moov Money");

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
            @NotNull @Min(1990) @Max(2100) Integer year,
            @NotBlank @Size(max = 150) String checkedBy,
            @NotNull @Min(0) @Max(10_000_000) Integer amount,
            @NotBlank @Size(max = 20) String paymentMode,
            boolean papersSeen,
            boolean numbersChecked) {}

    public record TheftRequest(
            @NotBlank @Size(max = 60) String q,
            @NotNull LocalDate date,
            @NotBlank @Size(max = 40) String commune,
            @NotBlank @Size(max = 200) String place,
            @Size(max = 1000) String details) {}

    private final MotoRepository motos;
    private final TheftRepository thefts;
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public BureauController(MotoRepository motos, TheftRepository thefts, AccountRepository accounts,
                            PasswordEncoder encoder, AuditService audit) {
        this.motos = motos;
        this.thefts = thefts;
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
    }

    private Account me() {
        String login = SecurityContextHolder.getContext().getAuthentication().getName();
        return accounts.findByLoginIgnoreCase(login).orElseThrow();
    }

    private static String newPin() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }

    private String newReference() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder("MS-");
            for (int i = 0; i < 8; i++) {
                if (i == 4) sb.append('-');
                sb.append(REF_ALPHABET.charAt(RANDOM.nextInt(REF_ALPHABET.length())));
            }
            if (!motos.existsByReference(sb.toString())) return sb.toString();
        }
        throw new ApiException(500, "REFERENCE_ERROR");
    }

    @PostMapping("/motos")
    @Transactional
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest r, HttpServletRequest req) {
        Account who = me();
        String ph = AccountService.phone8(r.phone());
        if (ph == null) throw new ApiException(400, "INVALID_PHONE");
        if (!PublicController.COMMUNES.contains(r.commune())) throw new ApiException(400, "INVALID_COMMUNE");
        if (r.year() > LocalDate.now().getYear() + 1) throw new ApiException(400, "INVALID_YEAR");
        if (!r.papersSeen() || !r.numbersChecked()) throw new ApiException(400, "CHECKS_REQUIRED");
        if (!MODES.contains(r.paymentMode())) throw new ApiException(400, "INVALID_PAYMENT_MODE");
        String checker = r.checkedBy().trim();
        if (checker.equalsIgnoreCase(who.name)) throw new ApiException(400, "SAME_CHECKER");
        if (!accounts.existsByNameIgnoreCaseAndSectionAndActiveTrue(checker, who.section)) {
            throw new ApiException(400, "CHECKER_UNKNOWN");
        }
        String plate = PublicController.norm(r.plate());
        String chassis = PublicController.norm(r.chassis());
        String engine = PublicController.norm(r.engine());
        if (plate.length() < 4 || chassis.length() < 5 || engine.length() < 4) throw new ApiException(400, "INVALID_NUMBERS");
        if (motos.existsByPlate(plate)) throw new ApiException(409, "PLATE_EXISTS");
        if (motos.existsByChassis(chassis)) throw new ApiException(409, "CHASSIS_EXISTS");
        if (motos.existsByEngine(engine)) throw new ApiException(409, "ENGINE_EXISTS");

        String pin = newPin();
        Moto m = new Moto();
        m.reference = newReference();
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
        m.pinHash = encoder.encode(pin);
        m.section = who.section;
        m.registeredByLogin = who.login;
        m.registeredByName = who.name;
        m.checkedBy = checker;
        m.paidAmount = r.amount();
        m.paymentMode = r.paymentMode();
        motos.save(m);
        m.receiptNo = "REC-" + LocalDate.now().getYear() + "-" + String.format("%05d", m.id);
        audit.log(who.login, "MOTO_REGISTERED", m.reference + " " + m.plate + " · " + m.receiptNo + " · " + m.section
                + " · relu par " + checker, req.getRemoteAddr());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.id);
        out.put("reference", m.reference);
        out.put("receipt", m.receiptNo);
        out.put("pin", pin); // affiché une seule fois, jamais stocké en clair
        out.put("plate", m.plate);
        out.put("ownerName", m.ownerName);
        out.put("ownerPhone", m.ownerPhone);
        out.put("brand", m.brand);
        out.put("model", m.model);
        out.put("color", m.color);
        out.put("year", m.year);
        out.put("chassis", m.chassis);
        out.put("engine", m.engine);
        out.put("commune", m.commune);
        out.put("section", m.section);
        out.put("agent", m.registeredByName);
        out.put("checkedBy", m.checkedBy);
        out.put("amount", m.paidAmount);
        out.put("paymentMode", m.paymentMode);
        out.put("registeredAt", m.registeredAt);
        return ResponseEntity.status(201).body(out);
    }

    /** Les dernières motos enregistrées par l'agent connecté (sans les montants). */
    @GetMapping("/motos/mine")
    public List<Map<String, Object>> mine() {
        return motos.findTop30ByRegisteredByLoginOrderByRegisteredAtDesc(me().login).stream().map(m -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.id);
            o.put("receipt", m.receiptNo);
            o.put("reference", m.reference);
            o.put("plate", m.plate);
            o.put("ownerName", m.ownerName);
            o.put("registeredAt", m.registeredAt);
            return o;
        }).toList();
    }

    /** Reçu perdu : un nouveau code secret est généré et l'ancien cesse de fonctionner. */
    @PostMapping("/motos/{id}/reset-pin")
    @Transactional
    public ResponseEntity<?> resetPin(@PathVariable Long id, HttpServletRequest req) {
        Account who = me();
        if (who.role != ml.motosur.domain.Role.BUREAU_RESP) throw new ApiException(403, "RESP_ONLY");
        Moto m = motos.findById(id).orElse(null);
        if (m == null) throw new ApiException(404, "NOT_FOUND");
        String pin = newPin();
        m.pinHash = encoder.encode(pin);
        m.pinFails = 0;
        m.pinLockedUntil = null;
        audit.log(who.login, "PIN_RESET", m.reference + " " + m.plate, req.getRemoteAddr());
        return ResponseEntity.ok(Map.of("reference", m.reference, "plate", m.plate, "pin", pin));
    }

    /** Déclaration de vol faite au bureau, pour le compte du propriétaire présent. */
    @PostMapping("/thefts")
    @Transactional
    public ResponseEntity<?> declareTheft(@Valid @RequestBody TheftRequest r, HttpServletRequest req) {
        Account who = me();
        if (!PublicController.COMMUNES.contains(r.commune())) throw new ApiException(400, "INVALID_COMMUNE");
        if (r.date().isAfter(LocalDate.now()) || r.date().isBefore(LocalDate.now().minusYears(1))) {
            throw new ApiException(400, "INVALID_DATE");
        }
        String n = PublicController.norm(r.q());
        if (n.length() < 4) throw new ApiException(400, "INVALID_NUMBERS");
        Moto m = motos.findByReference(PublicController.formatRef(r.q()))
                .or(() -> motos.findFirstByPlateOrChassisOrEngine(n, n, n)).orElse(null);
        if (m == null) throw new ApiException(404, "NOT_FOUND");
        if (m.status == Moto.Status.STOLEN) throw new ApiException(409, "ALREADY_STOLEN");
        Theft t = new Theft();
        t.moto = m;
        t.reference = "VOL-" + LocalDate.now().getYear() + "-" + String.format("%06d", RANDOM.nextInt(1_000_000));
        t.theftDate = r.date();
        t.commune = r.commune();
        t.place = r.place().trim();
        t.details = r.details() == null || r.details().isBlank() ? null : r.details().trim();
        thefts.save(t);
        m.status = Moto.Status.STOLEN;
        motos.save(m);
        audit.log(who.login, "THEFT_DECLARED", t.reference + " " + m.plate + " (au bureau " + who.section + ")",
                req.getRemoteAddr());
        return ResponseEntity.status(201).body(Map.of("reference", t.reference, "plate", m.plate));
    }
}

package ml.motosur.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ml.motosur.domain.Account;
import ml.motosur.domain.AccountRepository;
import ml.motosur.domain.Moto;
import ml.motosur.domain.MotoRepository;
import ml.motosur.domain.Role;
import ml.motosur.domain.Theft;
import ml.motosur.domain.TheftRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Tableau de bord de la Direction générale (rôle CENTRAL). */
@RestController
@RequestMapping("/api/central")
public class CentralController {

    private final MotoRepository motos;
    private final TheftRepository thefts;
    private final AccountRepository accounts;

    public CentralController(MotoRepository motos, TheftRepository thefts, AccountRepository accounts) {
        this.motos = motos;
        this.thefts = thefts;
        this.accounts = accounts;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        long stolen = motos.countByStatus(Moto.Status.STOLEN);
        long recovered = motos.countByStatus(Moto.Status.RECOVERED);
        long total = motos.count();
        long declared = stolen + recovered;

        Map<String, long[]> byCommune = new LinkedHashMap<>();
        for (String c : PublicController.COMMUNES) byCommune.put(c, new long[3]); // enregistrées, volées, retrouvées
        for (Moto m : motos.findAll()) {
            long[] a = byCommune.get(m.commune);
            if (a == null) continue;
            a[0]++;
        }
        List<Map<String, Object>> active = new ArrayList<>();
        for (Theft t : thefts.findAllWithMoto()) {
            long[] a = byCommune.get(t.commune);
            if (t.recoveredAt == null) {
                if (a != null) a[1]++;
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("reference", t.reference);
                o.put("plate", t.moto.plate);
                o.put("brand", t.moto.brand + " " + t.moto.model);
                o.put("theftDate", t.theftDate);
                o.put("commune", t.commune);
                o.put("place", t.place);
                o.put("ownerName", t.moto.ownerName);
                o.put("ownerPhone", t.moto.ownerPhone);
                active.add(o);
            } else if (a != null) {
                a[2]++;
            }
        }
        List<Map<String, Object>> communes = new ArrayList<>();
        byCommune.forEach((name, a) -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("commune", name);
            o.put("registered", a[0]);
            o.put("stolen", a[1]);
            o.put("recovered", a[2]);
            communes.add(o);
        });
        List<Map<String, Object>> commissariats = new ArrayList<>();
        for (Account a : accounts.findAll()) {
            if (a.role != Role.COMMISSARIAT) continue;
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", a.commissariat);
            o.put("login", a.login);
            o.put("active", a.active);
            o.put("lastLoginAt", a.lastLoginAt);
            commissariats.add(o);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("registered", total);
        out.put("stolen", stolen);
        out.put("recovered", recovered);
        out.put("recoveryRate", declared == 0 ? 0 : Math.round(recovered * 100.0 / declared));
        out.put("communes", communes);
        out.put("activeThefts", active);
        out.put("commissariats", commissariats);
        return out;
    }
}

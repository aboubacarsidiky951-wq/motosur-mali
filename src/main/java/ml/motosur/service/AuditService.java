package ml.motosur.service;

import ml.motosur.domain.AuditLog;
import ml.motosur.domain.AuditRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    public void log(String actor, String action, String detail, String ip) {
        AuditLog l = new AuditLog();
        l.actor = cut(actor == null ? "anonyme" : actor, 120);
        l.action = cut(action, 40);
        l.detail = detail == null ? null : cut(detail, 480);
        l.ip = ip == null ? null : cut(ip, 64);
        repo.save(l);
    }

    private static String cut(String s, int max) {
        String clean = s.replaceAll("[\\r\\n\\t]", " ");
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}

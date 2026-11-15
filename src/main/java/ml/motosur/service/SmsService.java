package ml.motosur.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Envoi de SMS par Twilio. Inactif tant que TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN et TWILIO_FROM
 * ne sont pas renseignés dans Render : dans ce cas, send() renvoie false et rien n'est envoyé.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final String sid;
    private final String token;
    private final String from;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public SmsService(@Value("${motosur.sms.twilio.sid:}") String sid,
                      @Value("${motosur.sms.twilio.token:}") String token,
                      @Value("${motosur.sms.twilio.from:}") String from) {
        this.sid = sid == null ? "" : sid.trim();
        this.token = token == null ? "" : token.trim();
        this.from = from == null ? "" : from.trim();
    }

    public boolean enabled() {
        return !sid.isEmpty() && !token.isEmpty() && !from.isEmpty();
    }

    /** @param phone8 numéro malien à 8 chiffres */
    public boolean send(String phone8, String body) {
        if (!enabled()) {
            return false;
        }
        try {
            String form = "To=" + enc("+223" + phone8) + "&From=" + enc(from) + "&Body=" + enc(body);
            String auth = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = res.statusCode() == 201 || res.statusCode() == 200;
            if (!ok) {
                log.warn("Twilio a refusé l'envoi (HTTP {})", res.statusCode());
            }
            return ok;
        } catch (Exception e) {
            log.warn("Envoi SMS impossible : {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

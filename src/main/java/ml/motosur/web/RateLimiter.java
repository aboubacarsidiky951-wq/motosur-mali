package ml.motosur.web;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Limite simple en mémoire : au plus {max} appels par clé (adresse IP) sur {windowMs} millisecondes. */
final class RateLimiter {
    private final int max;
    private final long windowMs;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    RateLimiter(int max, long windowMs) {
        this.max = max;
        this.windowMs = windowMs;
    }

    /** Vrai si la clé a déjà atteint sa limite (sans enregistrer d'appel). */
    boolean blocked(String key) {
        Deque<Long> q = hits.get(key);
        if (q == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
            return q.size() >= max;
        }
    }

    /** Enregistre un appel, même au-delà de la limite. */
    void hit(String key) {
        if (hits.size() > 10_000) {
            hits.clear();
        }
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(System.currentTimeMillis());
        }
    }

    boolean allow(String key) {
        if (blocked(key)) {
            return false;
        }
        hit(key);
        return true;
    }
}

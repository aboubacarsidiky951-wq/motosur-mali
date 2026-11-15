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

    boolean allow(String key) {
        long now = System.currentTimeMillis();
        if (hits.size() > 10_000) {
            hits.clear();
        }
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}

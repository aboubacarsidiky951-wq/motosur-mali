# MotoSûr Mali — étape 1 : serveur + compte de maintenance

Java 21 / Spring Boot 3.5 / PostgreSQL. Déploiement : Render (render.yaml + Dockerfile).

- Page de maintenance : `/maintenance.html` (réservée au rôle MAINTENANCE)
- Santé : `/api/health`
- Variables Render : `MAINTENANCE_EMAIL`, `MAINTENANCE_PASSWORD` (secret, 12 caractères min., saisi dans Render), `DB_*` (automatique)
- Local : `mvn spring-boot:run` (base H2 en mémoire, compte de dev uniquement)

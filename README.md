# MotoSûr Mali — étape 1 : serveur + compte de maintenance

Java 21 / Spring Boot 3.5 / PostgreSQL. Déploiement : Render (render.yaml + Dockerfile).

- Page de maintenance : `/maintenance.html` (réservée au rôle MAINTENANCE)
- Santé : `/api/health`
- Variables Render : `MAINTENANCE_EMAIL`, `MAINTENANCE_PASSWORD` (secret, 12 caractères min., saisi dans Render), `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (base Neon, saisis dans Render), `DB_PORT` (5432)
- Local : `mvn spring-boot:run` (base H2 en mémoire, compte de dev uniquement)


## V2 — Bureau MotoSûr
- Plus d'inscription publique : les motos sont enregistrées uniquement au bureau (comptes BUREAU_RESP / BUREAU_AGENT). Le propriétaire reçoit un reçu avec un numéro MS-XXXX-XXXX et un code secret à 4 chiffres.
- Comptes créés par lien d'invitation unique (48 h, usage unique) : maintenance → direction générale / bureaux → commissariats → agents ; responsable de bureau → agents du bureau.
- Caisse et journal du bureau : page Maintenance uniquement.
- Déployer avec une base neuve (nouveaux rôles) : Neon PostgreSQL.

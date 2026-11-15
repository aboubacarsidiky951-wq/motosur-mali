package ml.motosur.domain;

/** Niveaux d'accès. Le public n'a pas de compte. */
public enum Role {
    MAINTENANCE,   // propriétaire technique du site
    CENTRAL,       // Direction générale de la Police nationale
    COMMISSARIAT,  // un commissariat de Bamako
    AGENT          // agent de terrain
}

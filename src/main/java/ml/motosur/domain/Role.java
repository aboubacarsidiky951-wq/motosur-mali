package ml.motosur.domain;

/** Niveaux d'accès. Le public n'a pas de compte. */
public enum Role {
    MAINTENANCE,   // propriétaire technique du site (voit aussi la caisse du bureau)
    CENTRAL,       // Direction générale de la Police nationale
    COMMISSARIAT,  // un commissariat de Bamako
    AGENT,         // agent de terrain
    BUREAU_RESP,   // responsable d'un bureau d'enregistrement
    BUREAU_AGENT   // agent ou secrétaire d'un bureau d'enregistrement
}

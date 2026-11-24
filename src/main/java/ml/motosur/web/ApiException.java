package ml.motosur.web;

/** Erreur prévue : le code est renvoyé au site sous la forme {"error": "CODE"}. */
public class ApiException extends RuntimeException {
    public final int status;
    public final String code;

    public ApiException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}

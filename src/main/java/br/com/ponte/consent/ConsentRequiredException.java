package br.com.ponte.consent;

/** Lançada quando uma operação exige consentimento ativo e ele não existe. */
public class ConsentRequiredException extends RuntimeException {
    public ConsentRequiredException(String message) {
        super(message);
    }
}

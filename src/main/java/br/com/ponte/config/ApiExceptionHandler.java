package br.com.ponte.config;

import br.com.ponte.consent.ConsentRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ConsentRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> consentRequired(ConsentRequiredException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidBody(MethodArgumentNotValidException ex) {
        return Map.of("error", "Corpo da requisição inválido: "
                + ex.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + " " + f.getDefaultMessage())
                    .reduce((a, b) -> a + "; " + b).orElse(""));
    }
}

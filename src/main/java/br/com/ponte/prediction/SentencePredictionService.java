package br.com.ponte.prediction;

import br.com.ponte.symbol.Symbol;
import java.util.List;

/**
 * Prediz frases completas a partir da sequência de símbolos tocados,
 * reduzindo a carga motora (2-3 toques → frase inteira).
 * Implementações: stub local por templates ou API externa (LLM).
 */
public interface SentencePredictionService {

    /** @return até 3 frases sugeridas; vazia se a sequência tiver menos de 2 símbolos. */
    List<String> predict(List<Symbol> sequence);
}

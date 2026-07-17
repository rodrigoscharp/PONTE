package br.com.ponte.prediction;

import br.com.ponte.symbol.ImageType;
import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StubSentencePredictionServiceTest {

    private final StubSentencePredictionService service = new StubSentencePredictionService();

    private Symbol symbol(String label, SymbolCategory category) {
        return new Symbol(label, category, ImageType.EMOJI, "🔹", 0, null);
    }

    @Test
    void menosDeDoisSimbolosNaoSugereNada() {
        assertThat(service.predict(List.of(symbol("maçã", SymbolCategory.COMIDA)))).isEmpty();
    }

    @Test
    void queroMaisComidaSugereFraseDeComer() {
        List<String> suggestions = service.predict(List.of(
                symbol("quero", SymbolCategory.ACOES),
                symbol("maçã", SymbolCategory.COMIDA)));

        assertThat(suggestions).contains("Eu quero comer maçã");
    }

    @Test
    void sentimentoSugereEuEstou() {
        List<String> suggestions = service.predict(List.of(
                symbol("eu", SymbolCategory.PESSOAS),
                symbol("feliz", SymbolCategory.SENTIMENTOS)));

        assertThat(suggestions).contains("Eu estou feliz");
    }

    @Test
    void nuncaRetornaMaisDeTresSugestoes() {
        List<String> suggestions = service.predict(List.of(
                symbol("mamãe", SymbolCategory.PESSOAS),
                symbol("quero", SymbolCategory.ACOES),
                symbol("maçã", SymbolCategory.COMIDA),
                symbol("feliz", SymbolCategory.SENTIMENTOS)));

        assertThat(suggestions).hasSizeLessThanOrEqualTo(3).isNotEmpty();
    }
}

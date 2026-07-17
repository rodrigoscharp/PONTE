package br.com.ponte.prediction;

import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Stub de predição por templates por categoria — determinístico e offline.
 *
 * TODO: substituir pela integração real (LLM) quando
 * ponte.ai.prediction.api-key estiver configurada (ver AiConfig).
 */
public class StubSentencePredictionService implements SentencePredictionService {

    private static final int MAX_SUGGESTIONS = 3;

    @Override
    public List<String> predict(List<Symbol> sequence) {
        if (sequence.size() < 2) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();

        last(sequence, SymbolCategory.COMIDA)
                .ifPresent(c -> suggestions.add("Eu quero comer " + c.getLabel()));
        last(sequence, SymbolCategory.SENTIMENTOS)
                .ifPresent(s -> suggestions.add("Eu estou " + s.getLabel()));

        Optional<Symbol> pessoa = last(sequence, SymbolCategory.PESSOAS);
        Optional<Symbol> acao = last(sequence, SymbolCategory.ACOES);
        if (pessoa.isPresent() && acao.isPresent() && !pessoa.get().getLabel().equals("eu")) {
            suggestions.add(pessoa.get().getLabel() + ", eu quero " + acao.get().getLabel());
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Eu quero " + sequence.stream()
                    .map(Symbol::getLabel)
                    .collect(Collectors.joining(" ")));
        }
        return suggestions.stream().limit(MAX_SUGGESTIONS).toList();
    }

    private Optional<Symbol> last(List<Symbol> sequence, SymbolCategory category) {
        for (int i = sequence.size() - 1; i >= 0; i--) {
            if (sequence.get(i).getCategory() == category) {
                return Optional.of(sequence.get(i));
            }
        }
        return Optional.empty();
    }
}

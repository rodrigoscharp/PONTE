package br.com.ponte.prediction;

import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/predictions")
public class PredictionController {

    public record PredictionRequest(@NotNull Long childId, @NotEmpty List<Long> symbolIds) {}
    public record PredictionResponse(List<String> suggestions) {}

    private final SymbolRepository symbols;
    private final SentencePredictionService predictionService;

    public PredictionController(SymbolRepository symbols, SentencePredictionService predictionService) {
        this.symbols = symbols;
        this.predictionService = predictionService;
    }

    @PostMapping
    public PredictionResponse predict(@Valid @RequestBody PredictionRequest request) {
        Map<Long, Symbol> byId = symbols.findAllById(request.symbolIds()).stream()
                .collect(Collectors.toMap(Symbol::getId, Function.identity()));
        // preserva a ordem em que a criança tocou os símbolos
        List<Symbol> sequence = request.symbolIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return new PredictionResponse(predictionService.predict(sequence));
    }
}

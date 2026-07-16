package br.com.ponte.symbol;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/symbols")
public class SymbolController {

    public record SymbolResponse(Long id, String label, String category,
                                 String imageType, String imageRef, int gridPosition) {
        static SymbolResponse from(Symbol s) {
            return new SymbolResponse(s.getId(), s.getLabel(), s.getCategory().name(),
                    s.getImageType().name(), s.getImageRef(), s.getGridPosition());
        }
    }

    private final SymbolService symbolService;

    public SymbolController(SymbolService symbolService) {
        this.symbolService = symbolService;
    }

    @GetMapping
    public List<SymbolResponse> board(@RequestParam Long childId) {
        return symbolService.boardFor(childId).stream().map(SymbolResponse::from).toList();
    }
}

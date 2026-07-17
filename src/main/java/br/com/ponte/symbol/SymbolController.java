package br.com.ponte.symbol;

import br.com.ponte.picto.PictogramGenerationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final PictogramGenerationService pictograms;

    public SymbolController(SymbolService symbolService, PictogramGenerationService pictograms) {
        this.symbolService = symbolService;
        this.pictograms = pictograms;
    }

    @GetMapping
    public List<SymbolResponse> board(@RequestParam Long childId) {
        return symbolService.boardFor(childId).stream().map(SymbolResponse::from).toList();
    }

    /**
     * Foto do universo da criança → pictograma (stub/IA) → símbolo novo
     * SEMPRE no fim da grade (motor planning: nunca reorganiza).
     */
    @PostMapping(value = "/custom", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SymbolResponse addCustom(@RequestParam Long childId,
                                    @RequestParam String label,
                                    @RequestParam(defaultValue = "PERSONALIZADO") SymbolCategory category,
                                    @RequestPart("photo") MultipartFile photo) throws IOException {
        String imageRef = pictograms.generate(photo.getBytes(), label);
        Symbol symbol = symbolService.addSymbol(childId, label, category, ImageType.AI_GENERATED, imageRef);
        return SymbolResponse.from(symbol);
    }
}

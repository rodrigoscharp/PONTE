package br.com.ponte.symbol;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SymbolService {

    private final SymbolRepository symbols;

    public SymbolService(SymbolRepository symbols) {
        this.symbols = symbols;
    }

    public List<Symbol> boardFor(Long childId) {
        return symbols.boardFor(childId);
    }

    /**
     * Único caminho para criar símbolos: a posição é sempre max + 1 da
     * prancha da criança. Não existe API para mover ou reordenar.
     */
    @Transactional
    public Symbol addSymbol(Long childId, String label, SymbolCategory category,
                            ImageType imageType, String imageRef) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("O nome do símbolo é obrigatório.");
        }
        int nextPosition = symbols.maxGridPosition(childId) + 1;
        return symbols.save(new Symbol(label.trim(), category, imageType, imageRef, nextPosition, childId));
    }
}

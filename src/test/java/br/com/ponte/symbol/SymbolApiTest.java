package br.com.ponte.symbol;

import br.com.ponte.profile.ChildProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SymbolApiTest {

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired SymbolRepository symbols;
    @Autowired SymbolService symbolService;

    private Long seedChildId() {
        return profiles.findAll().get(0).getId();
    }

    @Test
    void seedCriaPranchaComDezesseisSimbolosOrdenados() throws Exception {
        mvc.perform(get("/api/v1/symbols").param("childId", seedChildId().toString()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(16))
           .andExpect(jsonPath("$[0].gridPosition").value(0))
           .andExpect(jsonPath("$[15].gridPosition").value(15))
           .andExpect(jsonPath("$[0].label").value("maçã"));
    }

    @Test
    void adicionarSimboloNuncaMoveOsExistentes() {
        Long childId = seedChildId();
        List<Symbol> before = symbols.boardFor(childId);

        Symbol added = symbolService.addSymbol(
                childId, "tablet", SymbolCategory.PERSONALIZADO, ImageType.EMOJI, "📱");

        // novo símbolo entra SEMPRE no fim (motor planning)
        assertThat(added.getGridPosition()).isEqualTo(before.size());

        List<Symbol> after = symbols.boardFor(childId);
        assertThat(after).hasSize(before.size() + 1);
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i).getId()).isEqualTo(before.get(i).getId());
            assertThat(after.get(i).getGridPosition()).isEqualTo(before.get(i).getGridPosition());
        }
    }

    @Test
    void simboloPersonalizadoDeOutraCriancaNaoApareceNaPrancha() {
        Long childId = seedChildId();
        symbolService.addSymbol(childId + 999, "outro", SymbolCategory.PERSONALIZADO, ImageType.EMOJI, "❓");

        List<Symbol> board = symbols.boardFor(childId);
        assertThat(board).noneMatch(s -> "outro".equals(s.getLabel()));
    }
}

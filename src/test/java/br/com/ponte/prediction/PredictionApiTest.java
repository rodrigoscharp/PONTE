package br.com.ponte.prediction;

import br.com.ponte.profile.ChildProfileRepository;
import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PredictionApiTest {

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired SymbolRepository symbols;

    @Test
    void sugereFraseAPartirDosToques() throws Exception {
        Long childId = profiles.findAll().get(0).getId();
        Long quero = idByLabel(childId, "quero");
        Long maca = idByLabel(childId, "maçã");

        mvc.perform(post("/api/v1/predictions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "symbolIds": [%d, %d]}
                    """.formatted(childId, quero, maca)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.suggestions[0]").value("Eu quero comer maçã"));
    }

    private Long idByLabel(Long childId, String label) {
        return symbols.boardFor(childId).stream()
                .filter(s -> s.getLabel().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException(label)).getId();
    }
}

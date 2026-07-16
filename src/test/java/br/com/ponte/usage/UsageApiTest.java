package br.com.ponte.usage;

import br.com.ponte.profile.ChildProfile;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsageApiTest {

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired SymbolRepository symbols;

    private Long seedChildId() {
        return profiles.findAll().get(0).getId();
    }

    private Symbol symbolByLabel(String label) {
        List<Symbol> board = symbols.boardFor(seedChildId());
        return board.stream().filter(s -> s.getLabel().equals(label)).findFirst().orElseThrow();
    }

    private void tap(Long childId, Long symbolId) throws Exception {
        mvc.perform(post("/api/v1/usage-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "symbolId": %d, "eventType": "SYMBOL_TAP"}
                    """.formatted(childId, symbolId)))
           .andExpect(status().isCreated());
    }

    @Test
    void registraToqueComConsentimentoAtivo() throws Exception {
        tap(seedChildId(), symbolByLabel("maçã").getId());
    }

    @Test
    void rejeitaEventoSemConsentimento() throws Exception {
        ChildProfile semConsentimento = profiles.save(new ChildProfile("Novo"));

        mvc.perform(post("/api/v1/usage-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "symbolId": %d, "eventType": "SYMBOL_TAP"}
                    """.formatted(semConsentimento.getId(), symbolByLabel("maçã").getId())))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void toqueSemSymbolIdEhRejeitado() throws Exception {
        mvc.perform(post("/api/v1/usage-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "eventType": "SYMBOL_TAP"}
                    """.formatted(seedChildId())))
           .andExpect(status().isBadRequest());
    }

    @Test
    void resumoDoDiaContaToquesFrasesETopSimbolos() throws Exception {
        Long childId = seedChildId();
        Long maca = symbolByLabel("maçã").getId();
        Long banana = symbolByLabel("banana").getId();

        tap(childId, maca);
        tap(childId, maca);
        tap(childId, banana);
        mvc.perform(post("/api/v1/usage-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "eventType": "SENTENCE_SPOKEN"}
                    """.formatted(childId)))
           .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/usage/summary").param("childId", childId.toString()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.totalTaps").value(3))
           .andExpect(jsonPath("$.sentencesSpoken").value(1))
           .andExpect(jsonPath("$.predictionsAccepted").value(0))
           .andExpect(jsonPath("$.topSymbols[0].label").value("maçã"))
           .andExpect(jsonPath("$.topSymbols[0].count").value(2))
           .andExpect(jsonPath("$.topSymbols[0].category").value("COMIDA"));
    }
}

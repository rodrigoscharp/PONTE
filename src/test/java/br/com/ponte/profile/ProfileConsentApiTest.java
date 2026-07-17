package br.com.ponte.profile;

import br.com.ponte.consent.ConsentRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileConsentApiTest {

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired ConsentRecordRepository consents;

    @Test
    void listaPerfisComStatusDeConsentimento() throws Exception {
        ChildProfile p = profiles.save(new ChildProfile("Bia"));

        mvc.perform(get("/api/v1/profiles"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.id == %d)].displayName".formatted(p.getId())).value("Bia"))
           .andExpect(jsonPath("$[?(@.id == %d)].hasActiveConsent".formatted(p.getId())).value(false));
    }

    @Test
    void registraConsentimento() throws Exception {
        ChildProfile p = profiles.save(new ChildProfile("Bia"));

        mvc.perform(post("/api/v1/profiles/{id}/consent", p.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"guardianName": "Maria", "purpose": "Acompanhamento terapêutico"}
                    """))
           .andExpect(status().isCreated());

        assertThat(consents.existsByChildProfileIdAndRevokedAtIsNull(p.getId())).isTrue();
    }

    @Test
    void revogaConsentimento() throws Exception {
        ChildProfile p = profiles.save(new ChildProfile("Bia"));
        mvc.perform(post("/api/v1/profiles/{id}/consent", p.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"guardianName": "Maria", "purpose": "Acompanhamento terapêutico"}
                    """))
           .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/profiles/{id}/consent/revoke", p.getId()))
           .andExpect(status().isNoContent());

        assertThat(consents.existsByChildProfileIdAndRevokedAtIsNull(p.getId())).isFalse();
    }

    @Test
    void consentimentoSemResponsavelEhRejeitado() throws Exception {
        ChildProfile p = profiles.save(new ChildProfile("Bia"));

        mvc.perform(post("/api/v1/profiles/{id}/consent", p.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"guardianName": "", "purpose": "x"}
                    """))
           .andExpect(status().isBadRequest());
    }
}

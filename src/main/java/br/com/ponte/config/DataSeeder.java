package br.com.ponte.config;

import br.com.ponte.consent.ConsentRecord;
import br.com.ponte.consent.ConsentRecordRepository;
import br.com.ponte.profile.ChildProfile;
import br.com.ponte.profile.ChildProfileRepository;
import br.com.ponte.symbol.ImageType;
import br.com.ponte.symbol.SymbolCategory;
import br.com.ponte.symbol.SymbolService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import static br.com.ponte.symbol.SymbolCategory.*;

/**
 * Seed do MVP: um perfil demo com consentimento e a prancha padrão
 * (16 símbolos emoji) — sem dependência de API externa.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final ChildProfileRepository profiles;
    private final ConsentRecordRepository consents;
    private final SymbolService symbolService;

    public DataSeeder(ChildProfileRepository profiles, ConsentRecordRepository consents,
                      SymbolService symbolService) {
        this.profiles = profiles;
        this.consents = consents;
        this.symbolService = symbolService;
    }

    @Override
    public void run(String... args) {
        if (profiles.count() > 0) {
            return;
        }
        ChildProfile demo = profiles.save(new ChildProfile("Alex"));
        consents.save(new ConsentRecord(demo.getId(), "Responsável demo",
                "Registro de uso da prancha para acompanhamento terapêutico"));

        // símbolos globais (childId null): posições 0..15, nesta ordem
        seed(COMIDA, "maçã", "🍎");
        seed(COMIDA, "banana", "🍌");
        seed(COMIDA, "água", "💧");
        seed(COMIDA, "biscoito", "🍪");
        seed(SENTIMENTOS, "feliz", "😊");
        seed(SENTIMENTOS, "triste", "😢");
        seed(SENTIMENTOS, "bravo", "😠");
        seed(SENTIMENTOS, "cansado", "😴");
        seed(PESSOAS, "eu", "🙋");
        seed(PESSOAS, "mamãe", "👩");
        seed(PESSOAS, "papai", "👨");
        seed(PESSOAS, "professora", "🧑‍🏫");
        seed(ACOES, "quero", "🤲");
        seed(ACOES, "comer", "🍽️");
        seed(ACOES, "brincar", "🧸");
        seed(ACOES, "parar", "🛑");
    }

    private void seed(SymbolCategory category, String label, String emoji) {
        symbolService.addSymbol(null, label, category, ImageType.EMOJI, emoji);
    }
}

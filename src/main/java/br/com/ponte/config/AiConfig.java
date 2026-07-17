package br.com.ponte.config;

import br.com.ponte.picto.PictogramGenerationService;
import br.com.ponte.picto.StubPictogramGenerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Ponto único de troca entre stubs locais e integrações reais de IA.
 * As chaves ficam em application.yml (ponte.ai.*.api-key).
 */
@Configuration
public class AiConfig {

    @Bean
    public PictogramGenerationService pictogramGenerationService(
            @Value("${ponte.ai.pictogram.api-key}") String apiKey,
            @Value("${ponte.uploads.dir}") String uploadsDir) {
        // TODO: quando apiKey não estiver vazia, retornar a implementação real
        // que chama a API externa de geração de pictogramas.
        return new StubPictogramGenerationService(Path.of(uploadsDir));
    }
}

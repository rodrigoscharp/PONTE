package br.com.ponte.picto;

import java.io.IOException;

/**
 * Gera um pictograma a partir de uma foto do universo da criança.
 * Implementações: stub local (recorte quadrado) ou API externa de IA.
 */
public interface PictogramGenerationService {

    /**
     * @param photoBytes bytes da foto enviada
     * @param label nome do símbolo (contexto para a geração por IA)
     * @return caminho público da imagem gerada, ex.: /uploads/abc.png
     */
    String generate(byte[] photoBytes, String label) throws IOException;
}

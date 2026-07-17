package br.com.ponte.picto;

import br.com.ponte.profile.ChildProfileRepository;
import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomSymbolApiTest {

    @TempDir
    static Path tempUploads;

    @DynamicPropertySource
    static void uploadsDir(DynamicPropertyRegistry registry) {
        registry.add("ponte.uploads.dir", () -> tempUploads.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired SymbolRepository symbols;

    private byte[] fakePhoto() throws Exception {
        BufferedImage img = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void fotoViraSimboloNoFimDaPranchaSemMoverOsExistentes() throws Exception {
        Long childId = profiles.findAll().get(0).getId();
        List<Symbol> before = symbols.boardFor(childId);

        mvc.perform(multipart("/api/v1/symbols/custom")
                .file(new MockMultipartFile("photo", "dino.png", "image/png", fakePhoto()))
                .param("childId", childId.toString())
                .param("label", "dino"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.label").value("dino"))
           .andExpect(jsonPath("$.category").value("PERSONALIZADO"))
           .andExpect(jsonPath("$.imageType").value("AI_GENERATED"))
           .andExpect(jsonPath("$.gridPosition").value(before.size()));

        List<Symbol> after = symbols.boardFor(childId);
        assertThat(after).hasSize(before.size() + 1);
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i).getId()).isEqualTo(before.get(i).getId());
        }

        // o stub gravou o pictograma 512x512 no diretório de uploads
        String imageRef = after.get(after.size() - 1).getImageRef();
        assertThat(imageRef).startsWith("/uploads/");
        Path saved = tempUploads.resolve(imageRef.substring("/uploads/".length()));
        assertThat(Files.exists(saved)).isTrue();
        BufferedImage generated = ImageIO.read(saved.toFile());
        assertThat(generated.getWidth()).isEqualTo(512);
        assertThat(generated.getHeight()).isEqualTo(512);
    }

    @Test
    void arquivoQueNaoEhImagemEhRejeitado() throws Exception {
        Long childId = profiles.findAll().get(0).getId();

        mvc.perform(multipart("/api/v1/symbols/custom")
                .file(new MockMultipartFile("photo", "x.txt", "text/plain", "nada".getBytes()))
                .param("childId", childId.toString())
                .param("label", "coisa"))
           .andExpect(status().isBadRequest());
    }
}

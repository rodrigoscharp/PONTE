package br.com.ponte.picto;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stub de geração de pictograma: recorta a foto num quadrado central e
 * redimensiona para 512x512 — nenhuma chamada externa.
 *
 * TODO: substituir pela integração real de geração de pictograma por IA
 * quando ponte.ai.pictogram.api-key estiver configurada (ver AiConfig).
 */
public class StubPictogramGenerationService implements PictogramGenerationService {

    private static final int SIZE = 512;

    private final Path uploadsDir;

    public StubPictogramGenerationService(Path uploadsDir) {
        this.uploadsDir = uploadsDir;
    }

    @Override
    public String generate(byte[] photoBytes, String label) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(photoBytes));
        if (original == null) {
            throw new IllegalArgumentException("O arquivo enviado não é uma imagem válida.");
        }
        int side = Math.min(original.getWidth(), original.getHeight());
        int x = (original.getWidth() - side) / 2;
        int y = (original.getHeight() - side) / 2;
        BufferedImage square = original.getSubimage(x, y, side, side);

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(square, 0, 0, SIZE, SIZE, null);
        g.dispose();

        Files.createDirectories(uploadsDir);
        String filename = UUID.randomUUID() + ".png";
        ImageIO.write(out, "png", uploadsDir.resolve(filename).toFile());
        return "/uploads/" + filename;
    }
}

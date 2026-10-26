package dev.monkeypatch.rctiming.domain.sponsor;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SponsorLogoProcessorTest {

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static BufferedImage solid(int w, int h, int type) {
        BufferedImage image = new BufferedImage(w, h, type);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void largePhotoIsShrunkToFitKeepingAspect() throws IOException {
        BufferedImage out = decode(SponsorLogoProcessor.normalise(encode(solid(3000, 1000, BufferedImage.TYPE_INT_RGB), "jpg")));

        assertThat(out.getHeight()).isLessThanOrEqualTo(SponsorLogoProcessor.MAX_HEIGHT);
        assertThat(out.getWidth()).isLessThanOrEqualTo(SponsorLogoProcessor.MAX_WIDTH);
        assertThat((double) out.getWidth() / out.getHeight()).isBetween(2.95, 3.05);
    }

    @Test
    void smallLogoIsNeverScaledUp() throws IOException {
        BufferedImage out = decode(SponsorLogoProcessor.normalise(encode(solid(120, 60, BufferedImage.TYPE_INT_ARGB), "png")));

        assertThat(out.getWidth()).isEqualTo(120);
        assertThat(out.getHeight()).isEqualTo(60);
    }

    @Test
    void transparentMarginIsTrimmed() throws IOException {
        BufferedImage padded = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = padded.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(100, 50, 100, 80);
        g.dispose();

        BufferedImage out = decode(SponsorLogoProcessor.normalise(encode(padded, "png")));

        assertThat(out.getWidth()).isEqualTo(100);
        assertThat(out.getHeight()).isEqualTo(80);
        assertThat(out.getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void tooSmallIsRefused() throws IOException {
        byte[] tiny = encode(solid(20, 20, BufferedImage.TYPE_INT_ARGB), "png");

        assertThatThrownBy(() -> SponsorLogoProcessor.normalise(tiny))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("too small");
    }

    @Test
    void fullyTransparentIsRefused() throws IOException {
        byte[] empty = encode(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB), "png");

        assertThatThrownBy(() -> SponsorLogoProcessor.normalise(empty))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("transparent");
    }

    @Test
    void notAnImageIsRefused() {
        assertThatThrownBy(() -> SponsorLogoProcessor.normalise("<svg onload=alert(1)/>".getBytes()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a readable");
    }
}

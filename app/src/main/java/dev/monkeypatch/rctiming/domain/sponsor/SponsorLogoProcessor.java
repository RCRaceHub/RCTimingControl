package dev.monkeypatch.rctiming.domain.sponsor;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns an uploaded sponsor logo into the one shape the overlay's sponsor bar and the Overlay Designer preview expect
 * (#518): a PNG with transparency, in sRGB, no wider than {@link #MAX_WIDTH} or taller than {@link #MAX_HEIGHT}
 * pixels, with any empty margin trimmed so logos sit at a similar visual size. The sponsor bar shows a logo about 72
 * pixels tall, so the limits leave room for a high-density screen without storing a camera-sized photo.
 *
 * <p>Re-encoding also drops everything that is not pixels (EXIF, GPS, ICC profiles, and the DPI a design tool
 * recorded, which the browser ignores anyway: only the pixel size matters on screen). An image is never scaled up,
 * since that only blurs it; the bar fixes the displayed height instead. Pure and stateless: called by {@link
 * EventSponsorService#uploadLogo} on the request thread, and throws only {@link IllegalArgumentException}, which the
 * API answers as 400.
 */
final class SponsorLogoProcessor {

    /** Largest pixel width kept. */
    static final int MAX_WIDTH = 800;
    /** Largest pixel height kept. */
    static final int MAX_HEIGHT = 240;
    /** Smaller than this on either side (after trimming) is too small to read on stream. */
    static final int MIN_SIDE = 32;
    /** Refuse bigger sources before decoding them, so a small file that expands to gigabytes cannot exhaust memory. */
    private static final long MAX_SOURCE_PIXELS = 25_000_000L;
    /** Alpha at or below this counts as empty when trimming margins. */
    private static final int EMPTY_ALPHA = 8;

    private SponsorLogoProcessor() {
    }

    /**
     * Decodes, trims, fits and re-encodes the upload.
     *
     * @param source the uploaded file's bytes (PNG or JPEG)
     * @return the normalised PNG bytes
     * @throws IllegalArgumentException if the bytes are not a readable image, are too large to decode safely, have
     *                                  nothing visible in them or are smaller than {@link #MIN_SIDE} pixels
     */
    static byte[] normalise(byte[] source) {
        BufferedImage image = decode(source);
        BufferedImage trimmed = trim(toArgb(image));
        if (trimmed.getWidth() < MIN_SIDE || trimmed.getHeight() < MIN_SIDE) {
            throw new IllegalArgumentException("The logo is too small: it must be at least " + MIN_SIDE
                    + " pixels each way (it is " + trimmed.getWidth() + " x " + trimmed.getHeight() + ")");
        }
        BufferedImage fitted = fit(trimmed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(fitted, "png", out)) {
                throw new IllegalArgumentException("The logo could not be converted to PNG");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not process the logo: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] source) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("The file is not a readable PNG or JPEG image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_SOURCE_PIXELS) {
                    throw new IllegalArgumentException("The image is too large to process: "
                            + reader.getWidth(0) + " x " + reader.getHeight(0) + " pixels");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // CMYK JPEGs and corrupt files land here
            throw new IllegalArgumentException("The file is not a readable PNG or JPEG image (CMYK JPEGs are not "
                    + "supported: export the logo as RGB)", e);
        }
    }

    private static BufferedImage toArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return argb;
    }

    /** Crops fully transparent margins; an image with no visible pixel is refused. */
    private static BufferedImage trim(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((image.getRGB(x, y) >>> 24) > EMPTY_ALPHA) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            throw new IllegalArgumentException("The logo is completely transparent");
        }
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) {
            return image;
        }
        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Scales down to fit {@link #MAX_WIDTH} x {@link #MAX_HEIGHT} in halving steps, which keeps edges smooth. */
    private static BufferedImage fit(BufferedImage image) {
        double scale = Math.min(1.0, Math.min((double) MAX_WIDTH / image.getWidth(), (double) MAX_HEIGHT / image.getHeight()));
        int targetW = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage current = image;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != targetW || current.getHeight() != targetH) {
            current = resize(current, targetW, targetH);
        }
        if (current == image && image.getRaster().getParent() != null) {
            // A cropped view shares its parent's pixels; copy so only the logo is written
            current = resize(image, image.getWidth(), image.getHeight());
        }
        return current;
    }

    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}

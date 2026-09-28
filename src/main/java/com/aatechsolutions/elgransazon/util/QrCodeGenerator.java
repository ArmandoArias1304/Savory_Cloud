package com.aatechsolutions.elgransazon.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Small helper that renders a QR code as PNG bytes, used by the printable documents
 * (PDF tickets with their autofactura link and the printed menu with the digital menu
 * link). Kept in {@code util} so any future printable piece can reuse it instead of
 * copying the ZXing boilerplate again.
 */
public final class QrCodeGenerator {

    private QrCodeGenerator() {
    }

    /**
     * @param text content to encode (a URL)
     * @param size square size in pixels
     * @return a PNG image of the QR code
     * @throws Exception if the content cannot be encoded or written
     */
    public static byte[] png(String text, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints);

        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}

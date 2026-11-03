package com.isocode.township.complaint;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

public enum ImageType {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private static final byte[] JPEG_START = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_MARK = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final int WEBP_MARK_OFFSET = 8;

    private final String contentType;

    ImageType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<ImageType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, 0, JPEG_START)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_START)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, WEBP_MARK_OFFSET, WEBP_MARK)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        return content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}

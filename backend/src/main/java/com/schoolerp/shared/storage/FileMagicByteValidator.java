package com.schoolerp.shared.storage;

import com.schoolerp.shared.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates file extension, category rules, and binary magic-byte signatures to prevent
 * polyglot uploads, executable disguised as images/PDFs, or stored XSS via SVG/HTML.
 */
@Component
public class FileMagicByteValidator {

    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PNG = "image/png";
    public static final String MIME_WEBP = "image/webp";
    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_CSV = "text/csv";
    public static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public String validateAndDetectMime(
            String sanitizedFilename,
            byte[] headerBytes,
            FileCategory category,
            Set<String> schoolAllowedMimes) {
        if (headerBytes == null || headerBytes.length < 4) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Uploaded file is empty or corrupted");
        }
        String ext = extractExtension(sanitizedFilename);
        String detectedMime = detectMimeFromMagicBytes(headerBytes, ext);

        if (!matchesExtension(detectedMime, ext)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "File content signature (" + detectedMime + ") does not match extension ." + ext);
        }

        if (!category.allowedMimeTypes().contains(detectedMime)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "MIME type " + detectedMime + " is not permitted for category " + category.name());
        }

        if (schoolAllowedMimes != null && !schoolAllowedMimes.isEmpty() && !schoolAllowedMimes.contains(detectedMime)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "MIME type " + detectedMime + " is disabled by school storage policy");
        }

        return detectedMime;
    }

    public String detectMimeFromMagicBytes(byte[] bytes, String ext) {
        // Reject Windows PE ("MZ") and Linux ELF (0x7F 'E' 'L' 'F') immediately
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0x4D && (bytes[1] & 0xFF) == 0x5A) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Executable files are strictly prohibited");
        }
        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x7F
                && bytes[1] == 'E'
                && bytes[2] == 'L'
                && bytes[3] == 'F') {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Executable ELF binaries are strictly prohibited");
        }

        // Check for HTML / SVG / Script polyglot prefixes
        String asciiPrefix = new String(
                bytes, 0, Math.min(bytes.length, 512), StandardCharsets.ISO_8859_1)
                .trim()
                .toLowerCase(Locale.ROOT);
        if (asciiPrefix.startsWith("<!doctype html")
                || asciiPrefix.startsWith("<html")
                || asciiPrefix.startsWith("<svg")
                || asciiPrefix.startsWith("<script")
                || asciiPrefix.startsWith("<?xml") && asciiPrefix.contains("<svg")) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "HTML, SVG, and script documents are disallowed for security reasons");
        }

        // JPEG: FF D8 FF
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return MIME_JPEG;
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return MIME_PNG;
        }

        // WebP: "RIFF" .... "WEBP"
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return MIME_WEBP;
        }

        // PDF: "%PDF-"
        if (bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-') {
            return MIME_PDF;
        }

        // XLSX (OOXML ZIP container): 50 4B 03 04
        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x50
                && (bytes[1] & 0xFF) == 0x4B
                && (bytes[2] & 0xFF) == 0x03
                && (bytes[3] & 0xFF) == 0x04) {
            if ("xlsx".equals(ext)) {
                return MIME_XLSX;
            }
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Archive files are only permitted for .xlsx imports");
        }

        // CSV: plain text without binary null bytes
        if ("csv".equals(ext) && isPrintableText(bytes)) {
            return MIME_CSV;
        }

        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported or unrecognized file binary signature");
    }

    private static boolean isPrintableText(byte[] bytes) {
        int inspectLen = Math.min(bytes.length, 4096);
        for (int i = 0; i < inspectLen; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0x00) {
                return false;
            }
        }
        return true;
    }

    private static String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesExtension(String mimeType, String ext) {
        return switch (mimeType) {
            case MIME_JPEG -> "jpg".equals(ext) || "jpeg".equals(ext);
            case MIME_PNG -> "png".equals(ext);
            case MIME_WEBP -> "webp".equals(ext);
            case MIME_PDF -> "pdf".equals(ext);
            case MIME_CSV -> "csv".equals(ext);
            case MIME_XLSX -> "xlsx".equals(ext);
            default -> false;
        };
    }
}


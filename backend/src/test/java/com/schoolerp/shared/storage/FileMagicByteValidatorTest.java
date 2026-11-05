package com.schoolerp.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.schoolerp.shared.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FileMagicByteValidatorTest {

    private final FileMagicByteValidator validator = new FileMagicByteValidator();
    private final Set<String> allDefaultMimes = Set.of(
            FileMagicByteValidator.MIME_JPEG,
            FileMagicByteValidator.MIME_PNG,
            FileMagicByteValidator.MIME_WEBP,
            FileMagicByteValidator.MIME_PDF,
            FileMagicByteValidator.MIME_CSV,
            FileMagicByteValidator.MIME_XLSX);

    @Test
    void acceptsValidJpegPngWebpPdfCsvAndXlsxHeaders() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        assertEquals(
                FileMagicByteValidator.MIME_JPEG,
                validator.validateAndDetectMime("photo.jpg", jpeg, FileCategory.PROFILE_PHOTO, allDefaultMimes));

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        assertEquals(
                FileMagicByteValidator.MIME_PNG,
                validator.validateAndDetectMime("logo.png", png, FileCategory.SCHOOL_LOGO, allDefaultMimes));

        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        assertEquals(
                FileMagicByteValidator.MIME_WEBP,
                validator.validateAndDetectMime("avatar.webp", webp, FileCategory.PROFILE_PHOTO, allDefaultMimes));

        byte[] pdf = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
        assertEquals(
                FileMagicByteValidator.MIME_PDF,
                validator.validateAndDetectMime("certificate.pdf", pdf, FileCategory.STUDENT_DOC, allDefaultMimes));

        byte[] csv = "student_code,full_name\nSTD-01,Rahim\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(
                FileMagicByteValidator.MIME_CSV,
                validator.validateAndDetectMime("students.csv", csv, FileCategory.BULK_IMPORT, allDefaultMimes));

        byte[] xlsx = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        assertEquals(
                FileMagicByteValidator.MIME_XLSX,
                validator.validateAndDetectMime("students.xlsx", xlsx, FileCategory.BULK_IMPORT, allDefaultMimes));
    }

    @Test
    void rejectsExecutableHtmlAndSvgDisguisedAsImagesOrPdfs() {
        byte[] exeBytes = "MZ\u0090\u0000\u0003\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
        ApiException exeEx = assertThrows(
                ApiException.class,
                () -> validator.validateAndDetectMime("malware.pdf", exeBytes, FileCategory.STUDENT_DOC, allDefaultMimes));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exeEx.getStatus());

        byte[] svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"></svg>"
                .getBytes(StandardCharsets.UTF_8);
        ApiException svgEx = assertThrows(
                ApiException.class,
                () -> validator.validateAndDetectMime("xss.jpg", svgBytes, FileCategory.PROFILE_PHOTO, allDefaultMimes));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, svgEx.getStatus());

        byte[] htmlBytes = "<!DOCTYPE html><html><script> evil() </script></html>"
                .getBytes(StandardCharsets.UTF_8);
        ApiException htmlEx = assertThrows(
                ApiException.class,
                () -> validator.validateAndDetectMime("doc.pdf", htmlBytes, FileCategory.STUDENT_DOC, allDefaultMimes));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, htmlEx.getStatus());

        // Genuine PDF bytes uploaded with .jpg extension must also fail extension-signature match
        byte[] pdfBytes = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        ApiException mismatchEx = assertThrows(
                ApiException.class,
                () -> validator.validateAndDetectMime("spoofed.jpg", pdfBytes, FileCategory.STUDENT_DOC, allDefaultMimes));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, mismatchEx.getStatus());
    }
}


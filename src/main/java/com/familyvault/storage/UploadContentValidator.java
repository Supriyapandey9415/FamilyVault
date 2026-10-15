package com.familyvault.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Content checks, not an antivirus. Uploaded files remain untrusted even after validation. */
public final class UploadContentValidator {
    private UploadContentValidator() {}

    public static void validate(MultipartFile file, String extension) {
        try (var input = file.getInputStream()) {
            byte[] head = input.readNBytes(8192);
            boolean valid = switch (extension) {
                case "pdf" -> starts(head, "%PDF-".getBytes(StandardCharsets.US_ASCII));
                case "png" -> starts(head, new byte[]{(byte)137,80,78,71,13,10,26,10});
                case "jpg", "jpeg" -> starts(head, new byte[]{(byte)255,(byte)216,(byte)255});
                case "webp" -> head.length >= 12 && starts(head, "RIFF".getBytes(StandardCharsets.US_ASCII))
                        && Arrays.equals(Arrays.copyOfRange(head, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
                case "doc", "xls" -> starts(head, new byte[]{(byte)208,(byte)207,17,(byte)224,(byte)161,(byte)177,26,(byte)225});
                case "docx", "xlsx" -> validateOfficeArchive(file, extension);
                case "csv", "txt" -> isText(head);
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("File content does not match its supported file type");
        } catch (IOException exception) {
            throw new IllegalArgumentException("File content could not be validated");
        }
    }

    private static boolean starts(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }

    private static boolean isText(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return false;
        try {
            // A sample can end midway through a UTF-8 code point; validate its complete prefix.
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
            var output = java.nio.CharBuffer.allocate(bytes.length);
            return !decoder.decode(ByteBuffer.wrap(bytes), output, false).isError();
        } catch (RuntimeException exception) { return false; }
    }

    private static boolean validateOfficeArchive(MultipartFile file, String extension) throws IOException {
        boolean types = false, document = false;
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 2000) return false;
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("..") || name.startsWith("/") || name.contains("\\")
                        || name.endsWith("vbaproject.bin") || name.contains("/embeddings/")) return false;
                types |= name.equals("[content_types].xml");
                document |= name.equals(extension.equals("docx") ? "word/document.xml" : "xl/workbook.xml");
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 50L * 1024 * 1024) return false;
                }
            }
        }
        return types && document;
    }
}

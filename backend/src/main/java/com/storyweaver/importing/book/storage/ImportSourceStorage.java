package com.storyweaver.importing.book.storage;

import com.storyweaver.importing.book.config.TxtImportProperties;
import com.storyweaver.shared.error.ApiException;
import com.storyweaver.shared.error.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ImportSourceStorage {
    private final Path root;
    private final long maxBytes;

    public ImportSourceStorage(TxtImportProperties properties) {
        this.root = properties.storageDirectory().toAbsolutePath().normalize();
        this.maxBytes = properties.maxFileSize().toBytes();
    }

    public StoredFile store(InputStream input) {
        String key = UUID.randomUUID() + ".txt";
        Path target = resolve(key);
        try {
            Files.createDirectories(root);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream source = input;
                    OutputStream output =
                            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                int read;
                while ((read = source.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    total += read;
                    if (total > maxBytes) {
                        throw new BadRequestException("FILE_TOO_LARGE", "TXT file exceeds 20 MB");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            } catch (RuntimeException | IOException exception) {
                Files.deleteIfExists(target);
                throw exception;
            }
            if (total == 0) {
                Files.deleteIfExists(target);
                throw new BadRequestException("EMPTY_FILE", "TXT file is empty");
            }
            return new StoredFile(key, total, HexFormat.of().formatHex(digest.digest()));
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILURE", "TXT source could not be stored");
        }
    }

    public Path path(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("IMPORT_EXPIRED", "TXT source is no longer available");
        }
        Path value = resolve(key);
        if (!Files.isRegularFile(value)) {
            throw new BadRequestException("IMPORT_EXPIRED", "TXT source is no longer available");
        }
        return value;
    }

    public boolean delete(String key) {
        if (key == null || key.isBlank()) return false;
        try {
            return Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILURE", "TXT source could not be deleted");
        }
    }

    private Path resolve(String key) {
        Path value = root.resolve(key).normalize();
        if (!value.startsWith(root)) {
            throw new BadRequestException("STORAGE_FAILURE", "Invalid TXT storage key");
        }
        return value;
    }

    public record StoredFile(String storageKey, long sizeBytes, String sha256) {}
}

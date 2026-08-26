package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.InternalServerException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.config.StorageProperties;
import io.github.mainalisandeep.cvgen.enums.FileType;
import io.github.mainalisandeep.cvgen.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Blob backend backed by a directory on the application server.
 * <p>
 * Deliberately the simplest thing that satisfies {@link FileStorageService}: when this moves
 * to R2, that is a sibling implementation plus a property, and nothing above this class changes.
 */
@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final StorageProperties storageProperties;

    private Path root;

    @PostConstruct
    void prepareRoot() {
        root = Path.of(storageProperties.getLocalRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create storage root at " + root, e);
        }
    }

    @Override
    public String store(FileType fileType, byte[] content, String extension) {
        String key = fileType.name().toLowerCase(Locale.ROOT)
                + "/" + UUID.randomUUID()
                + (extension == null || extension.isBlank() ? "" : "." + extension);

        Path target = resolveWithinRoot(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new InternalServerException(ErrorConstantValue.FILE_STORAGE_FAILED);
        }
        return key;
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(resolveWithinRoot(storageKey));
        } catch (NoSuchFileException e) {
            // The row outlived its bytes. Nothing the caller can do differently, so it reads as a server fault.
            log.warn("Stored file missing on disk: {}", storageKey);
            throw new InternalServerException(ErrorConstantValue.FILE_STORAGE_FAILED);
        } catch (IOException e) {
            throw new InternalServerException(ErrorConstantValue.FILE_STORAGE_FAILED);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveWithinRoot(storageKey));
        } catch (IOException e) {
            // Cleanup is best-effort: a leaked blob is cheaper than failing the user's request.
            log.warn("Unable to delete stored file {}: {}", storageKey, e.getMessage());
        }
    }

    /**
     * Keys are generated here and never accepted from a request, but they do round-trip through
     * the database, so the containment check stays: one bad row must not turn into an arbitrary
     * filesystem read.
     */
    private Path resolveWithinRoot(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new InternalServerException(ErrorConstantValue.FILE_STORAGE_FAILED);
        }
        return resolved;
    }
}

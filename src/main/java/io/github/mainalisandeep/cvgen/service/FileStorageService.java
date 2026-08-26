package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.enums.FileType;

/**
 * The blob backend, kept behind an interface so the move from local disk to R2 is a new
 * implementation and a changed property - nothing above this line knows where bytes live.
 * <p>
 * Storage keys are opaque: callers persist what {@link #store} returns and hand it back
 * unchanged. They are not paths and must never be built by a caller.
 */
public interface FileStorageService {

    /**
     * Writes {@code content} and returns the key it can be read back at.
     *
     * @param extension file extension without the dot, or {@code null} for none
     */
    String store(FileType fileType, byte[] content, String extension);

    byte[] load(String storageKey);

    /** Silently does nothing when the key is already gone, so cleanup stays idempotent. */
    void delete(String storageKey);
}

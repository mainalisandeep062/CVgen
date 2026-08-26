package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.config.StorageProperties;
import io.github.mainalisandeep.cvgen.entity.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns a stored blob into a URL a browser can fetch.
 * <p>
 * Single place that knows the public shape of a file URL, so moving to R2 - where this
 * becomes a bucket or CDN address instead of an endpoint on this application - touches
 * nothing but this class.
 */
@Component
@RequiredArgsConstructor
public class FileUrlResolver {

    /** Unauthenticated read path for avatars; see MatchersConfig. */
    public static final String AVATAR_PATH = "/api/files/avatars/";

    private final StorageProperties storageProperties;

    /** @return {@code null} for a null file, so callers can pass an unset picture straight through */
    public String avatarUrl(StoredFile file) {
        if (file == null) {
            return null;
        }
        String base = storageProperties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return AVATAR_PATH + file.getId();
        }
        return base.replaceAll("/+$", "") + AVATAR_PATH + file.getId();
    }
}

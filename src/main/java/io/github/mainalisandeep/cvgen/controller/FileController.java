package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.records.BinaryContent;
import io.github.mainalisandeep.cvgen.service.ProfilePictureService;
import io.github.mainalisandeep.cvgen.service.impl.FileUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Serves stored blobs.
 * <p>
 * Only avatars are exposed, and only unauthenticated: a picture has to render from an
 * {@code <img src>}, which cannot carry a bearer token. Access rests on the id being an
 * unguessable UUID, which is acceptable for a picture the user publishes anyway. Anything
 * genuinely private - generated CVs above all - needs its own authenticated endpoint here
 * and must not be folded into this one.
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private static final Duration AVATAR_CACHE = Duration.ofDays(7);

    private final ProfilePictureService profilePictureService;

    /**
     * Cacheable for a long time without going stale: choosing a different picture creates a new
     * file with a new id, so a URL's bytes never change under a client.
     */
    @GetMapping(FileUrlResolver.AVATAR_PATH + "{fileId}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable UUID fileId) {
        BinaryContent content = profilePictureService.loadAvatar(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.maxAge(AVATAR_CACHE).cachePublic())
                .body(content.bytes());
    }
}

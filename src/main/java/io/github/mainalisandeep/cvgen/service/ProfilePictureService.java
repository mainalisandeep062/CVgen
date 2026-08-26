package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.ProfilePictureDto;
import io.github.mainalisandeep.cvgen.dto.ProfilePictureOptionsDto;
import io.github.mainalisandeep.cvgen.records.BinaryContent;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Owns the one column that decides what picture a user shows.
 * <p>
 * Every way of setting it - picking a linked provider's avatar, uploading - ends the same way:
 * the bytes are copied into our own storage and the user points at that copy. Nothing downstream
 * has to know which route was taken, and the picture stops depending on a CDN we do not control.
 */
public interface ProfilePictureService {

    /** @return URL for the current picture, or {@code null} when none is set */
    String getProfilePictureUrl(UUID userId);

    /** Current picture plus the linked-provider avatars it can be switched to. */
    ProfilePictureOptionsDto getOptions(UUID userId);

    /** Copies the avatar this identity reports and makes it the user's picture. */
    ProfilePictureDto selectFromIdentity(UUID userId, UUID identityId);

    ProfilePictureDto upload(UUID userId, MultipartFile file);

    void remove(UUID userId);

    /**
     * Seeds a picture from a provider only if the user has none, and never throws.
     * <p>
     * Runs after signup, off the login thread: a provider being slow must not slow down or
     * fail authentication.
     */
    void seedFromIdentityIfUnset(UUID userId, UUID identityId);

    /** Bytes for the public avatar endpoint. */
    BinaryContent loadAvatar(UUID fileId);
}

package io.github.mainalisandeep.cvgen.dto;

import java.util.UUID;

/**
 * The picture a user currently displays.
 *
 * @param fileId         id of the stored blob, {@code null} when no picture is set
 * @param url            absolute or same-origin URL the frontend can put straight in {@code <img src>}
 * @param sourceProvider provider the copy was taken from ("google"), or {@code null} for an upload
 */
public record ProfilePictureDto(UUID fileId, String url, String sourceProvider) {

    public static ProfilePictureDto none() {
        return new ProfilePictureDto(null, null, null);
    }
}

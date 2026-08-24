package io.github.mainalisandeep.cvgen.dto;

import java.util.UUID;

/**
 * One entry in the "change your picture" menu: the avatar a linked provider currently reports.
 * <p>
 * {@code avatarUrl} points at the provider's CDN and is fine to preview in the picker, but it
 * is not what gets displayed afterwards - selecting an option copies those bytes into our own
 * storage first.
 *
 * @param identityId id to send back to select this option
 * @param selected   whether the current picture was taken from this identity
 */
public record ProfilePictureOptionDto(UUID identityId, String provider, String avatarUrl, boolean selected) {
}

package io.github.mainalisandeep.cvgen.dto;

import java.util.List;

/**
 * Everything the picture picker needs: what is shown now, and what it can be changed to
 * without uploading anything.
 */
public record ProfilePictureOptionsDto(ProfilePictureDto current, List<ProfilePictureOptionDto> providerOptions) {
}

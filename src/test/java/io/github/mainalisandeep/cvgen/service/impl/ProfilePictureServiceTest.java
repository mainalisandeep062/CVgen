package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.repository.StoredFileRepository;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.ProfilePictureService;
import io.github.mainalisandeep.cvgen.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the invariant the whole design rests on: whatever route a picture arrives by, it ends
 * up as one row in {@code files} that the user points at, and the row it replaced is reclaimed.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProfilePictureServiceTest extends PostgresContainerSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    @Autowired
    private ProfilePictureService profilePictureService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userRepository.findAll().forEach(user -> {
            user.setProfilePictureFile(null);
            userRepository.save(user);
        });
        storedFileRepository.deleteAll();
        userIdentityRepository.deleteAll();
        userRepository.deleteAll();

        userId = userRepository.save(User.builder()
                .email("picture@example.com")
                .name("Picture User")
                .emailVerified(true)
                .build()).getId();
    }

    @Test
    @DisplayName("Upload stores one file and points the user at it")
    void uploadSetsPicture() {
        var result = profilePictureService.upload(userId, pngUpload("me.png"));

        assertThat(result.fileId()).isNotNull();
        assertThat(result.url()).endsWith(result.fileId().toString());
        assertThat(result.sourceProvider()).isNull();
        assertThat(storedFileRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Replacing a picture reclaims the one it replaced instead of leaking it")
    void replacingPictureDeletesPrevious() {
        UUID firstFileId = profilePictureService.upload(userId, pngUpload("first.png")).fileId();
        UUID secondFileId = profilePictureService.upload(userId, pngUpload("second.png")).fileId();

        assertThat(secondFileId).isNotEqualTo(firstFileId);
        assertThat(storedFileRepository.findById(firstFileId)).isEmpty();
        assertThat(storedFileRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Removing the picture clears the column and reclaims the file")
    void removeClearsPicture() {
        profilePictureService.upload(userId, pngUpload("gone.png"));

        profilePictureService.remove(userId);

        assertThat(profilePictureService.getProfilePictureUrl(userId)).isNull();
        assertThat(storedFileRepository.count()).isZero();
    }

    @Test
    @DisplayName("Uploads outside the allow-list are refused rather than sniffed")
    void rejectsDisallowedContentType() {
        MockMultipartFile pdf = new MockMultipartFile("file", "cv.pdf", "application/pdf", PNG);

        assertThatThrownBy(() -> profilePictureService.upload(userId, pdf))
                .isInstanceOf(BadRequestException.class);
        assertThat(storedFileRepository.count()).isZero();
    }

    @Test
    @DisplayName("An identity with no avatar cannot be selected")
    void selectingIdentityWithoutAvatarFails() {
        UUID identityId = userIdentityRepository.save(UserIdentity.builder()
                .user(userRepository.findById(userId).orElseThrow())
                .provider("github")
                .providerId("gh-no-avatar")
                .emailAtProvider("picture@example.com")
                .build()).getId();

        assertThatThrownBy(() -> profilePictureService.selectFromIdentity(userId, identityId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Options list every linked provider avatar and mark the selected one")
    void optionsListProviderAvatars() {
        userIdentityRepository.save(UserIdentity.builder()
                .user(userRepository.findById(userId).orElseThrow())
                .provider("google")
                .providerId("g-1")
                .emailAtProvider("picture@example.com")
                .avatarUrlAtProvider("https://cdn.google.test/a.png")
                .build());

        var options = profilePictureService.getOptions(userId);

        assertThat(options.current().fileId()).isNull();
        assertThat(options.providerOptions()).singleElement()
                .satisfies(option -> {
                    assertThat(option.provider()).isEqualTo("google");
                    assertThat(option.avatarUrl()).isEqualTo("https://cdn.google.test/a.png");
                    assertThat(option.selected()).isFalse();
                });
    }

    private MockMultipartFile pngUpload(String filename) {
        return new MockMultipartFile("file", filename, "image/png", PNG);
    }
}

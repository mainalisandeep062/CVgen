package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.config.StorageProperties;
import io.github.mainalisandeep.cvgen.dto.ProfilePictureDto;
import io.github.mainalisandeep.cvgen.dto.ProfilePictureOptionDto;
import io.github.mainalisandeep.cvgen.dto.ProfilePictureOptionsDto;
import io.github.mainalisandeep.cvgen.entity.StoredFile;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.enums.FileType;
import io.github.mainalisandeep.cvgen.records.BinaryContent;
import io.github.mainalisandeep.cvgen.repository.StoredFileRepository;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.FileStorageService;
import io.github.mainalisandeep.cvgen.service.ProfilePictureService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfilePictureServiceImpl implements ProfilePictureService {

    private static final Logger log = LoggerFactory.getLogger(ProfilePictureServiceImpl.class);

    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final RemoteImageFetcher remoteImageFetcher;
    private final FileUrlResolver fileUrlResolver;
    private final StorageProperties storageProperties;

    @Override
    @Transactional(readOnly = true)
    public String getProfilePictureUrl(UUID userId) {
        return fileUrlResolver.avatarUrl(findUser(userId).getProfilePictureFile());
    }

    @Override
    @Transactional(readOnly = true)
    public ProfilePictureOptionsDto getOptions(UUID userId) {
        User user = findUser(userId);
        StoredFile current = user.getProfilePictureFile();
        UUID selectedIdentityId = current == null || current.getSourceIdentity() == null
                ? null
                : current.getSourceIdentity().getId();

        List<ProfilePictureOptionDto> options = userIdentityRepository.findByUserId(userId).stream()
                .filter(identity -> identity.getAvatarUrlAtProvider() != null
                        && !identity.getAvatarUrlAtProvider().isBlank())
                .map(identity -> new ProfilePictureOptionDto(
                        identity.getId(),
                        identity.getProvider(),
                        identity.getAvatarUrlAtProvider(),
                        identity.getId().equals(selectedIdentityId)))
                .toList();

        return new ProfilePictureOptionsDto(toDto(current), options);
    }

    @Override
    @Transactional
    public ProfilePictureDto selectFromIdentity(UUID userId, UUID identityId) {
        User user = findUser(userId);
        UserIdentity identity = findOwnedIdentity(userId, identityId);

        String sourceUrl = identity.getAvatarUrlAtProvider();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new BadRequestException(ErrorConstantValue.PROFILE_PICTURE_UNAVAILABLE);
        }

        // Already holding a copy of exactly this URL from exactly this identity: re-downloading
        // would burn a request and churn storage to produce the same bytes.
        StoredFile current = user.getProfilePictureFile();
        if (current != null
                && current.getSourceIdentity() != null
                && identityId.equals(current.getSourceIdentity().getId())
                && sourceUrl.equals(current.getSourceUrl())) {
            return toDto(current);
        }

        BinaryContent fetched = remoteImageFetcher.fetch(sourceUrl)
                .orElseThrow(() -> new BadRequestException(ErrorConstantValue.PROFILE_PICTURE_FETCH_FAILED));

        StoredFile stored = persist(fetched, user, identity, sourceUrl);
        replacePicture(user, stored);
        return toDto(stored);
    }

    @Override
    @Transactional
    public ProfilePictureDto upload(UUID userId, MultipartFile file) {
        User user = findUser(userId);
        BinaryContent content = readUpload(file);
        StoredFile stored = persist(content, user, null, null);
        replacePicture(user, stored);
        return toDto(stored);
    }

    @Override
    @Transactional
    public void remove(UUID userId) {
        replacePicture(findUser(userId), null);
    }

    @Override
    @Transactional
    public void seedFromIdentityIfUnset(UUID userId, UUID identityId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getProfilePictureFile() != null) {
                return; // already has one, or the account went away - either way nothing to seed
            }
            selectFromIdentity(userId, identityId);
        } catch (Exception e) {
            // A missing default picture is cosmetic. It must never surface as a failed signup.
            log.debug("Could not seed profile picture for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BinaryContent loadAvatar(UUID fileId) {
        StoredFile file = storedFileRepository.findByIdAndFileType(fileId, FileType.PROFILE_PICTURE)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.PROFILE_PICTURE));

        return new BinaryContent(
                fileStorageService.load(file.getStorageKey()),
                file.getMimeType(),
                file.getOriginalFilename());
    }

    /**
     * Points the user at {@code replacement} and reclaims whatever they were pointing at.
     * <p>
     * The flush matters: the reference check below must see the new value, or the row being
     * replaced still looks in use. The delete happens only once nothing references the old
     * file, because {@code fk_users_profile_picture_file} is ON DELETE SET NULL - deleting a
     * shared row would quietly strip someone else's picture instead of failing.
     */
    private void replacePicture(User user, StoredFile replacement) {
        StoredFile previous = user.getProfilePictureFile();
        if (previous != null && replacement != null && previous.getId().equals(replacement.getId())) {
            return;
        }

        user.setProfilePictureFile(replacement);
        userRepository.saveAndFlush(user);

        if (previous == null || storedFileRepository.isReferencedAsProfilePicture(previous.getId())) {
            return;
        }
        storedFileRepository.delete(previous);
        fileStorageService.delete(previous.getStorageKey());
    }

    private StoredFile persist(BinaryContent content, User owner, UserIdentity sourceIdentity, String sourceUrl) {
        String extension = EXTENSION_BY_MIME.get(content.contentType());
        String storageKey = fileStorageService.store(FileType.PROFILE_PICTURE, content.bytes(), extension);

        return storedFileRepository.save(StoredFile.builder()
                .storageKey(storageKey)
                .fileType(FileType.PROFILE_PICTURE)
                .mimeType(content.contentType())
                .sizeBytes(content.bytes().length)
                .originalFilename(content.filename())
                .uploadedBy(owner)
                .sourceIdentity(sourceIdentity)
                .sourceUrl(sourceUrl)
                .build());
    }

    /** Validates before reading anything into memory that the configured ceiling would reject. */
    private BinaryContent readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorConstantValue.FILE_EMPTY);
        }
        if (file.getSize() > storageProperties.getUpload().getMaxProfilePictureBytes()) {
            throw new BadRequestException(ErrorConstantValue.FILE_TOO_LARGE);
        }

        String contentType = Optional.ofNullable(file.getContentType())
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .orElse("");
        // Allow-list, not a block-list: an unrecognised type is refused rather than guessed at.
        if (!storageProperties.getUpload().getAllowedImageMimeTypes().contains(contentType)) {
            throw new BadRequestException(ErrorConstantValue.FILE_TYPE_UNSUPPORTED);
        }

        try {
            return new BinaryContent(file.getBytes(), contentType, file.getOriginalFilename());
        } catch (IOException e) {
            throw new BadRequestException(ErrorConstantValue.FILE_EMPTY);
        }
    }

    private ProfilePictureDto toDto(StoredFile file) {
        if (file == null) {
            return ProfilePictureDto.none();
        }
        String provider = file.getSourceIdentity() == null ? null : file.getSourceIdentity().getProvider();
        return new ProfilePictureDto(file.getId(), fileUrlResolver.avatarUrl(file), provider);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));
    }

    /**
     * Not-found rather than forbidden for an identity owned by someone else: a 403 would
     * confirm the id exists.
     */
    private UserIdentity findOwnedIdentity(UUID userId, UUID identityId) {
        return userIdentityRepository.findById(identityId)
                .filter(identity -> identity.getUser().getId().equals(userId))
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER_IDENTITY));
    }
}

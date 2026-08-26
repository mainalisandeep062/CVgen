package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.ProfilePictureDto;
import io.github.mainalisandeep.cvgen.dto.ProfilePictureOptionsDto;
import io.github.mainalisandeep.cvgen.dto.UserResponseDto;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.ProfilePictureService;
import io.github.mainalisandeep.cvgen.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController extends BaseController {

    private final UserService userService;
    private final ProfilePictureService profilePictureService;
    private final JwtTokenUtil jwtTokenUtil;

    /** Profile of the currently authenticated user. */
    @GetMapping("/me")
    public ResponseEntity<GlobalApiResponse<UserResponseDto>> getCurrentUser() {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return ok(SuccessResponseConstant.FETCH_SUCCESS, userService.getUserById(userId), FieldConstantValue.USER);
    }

    @GetMapping("/profile-picture-url")
    public ResponseEntity<GlobalApiResponse<String>> getProfilePictureUrl() {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return ok(SuccessResponseConstant.FETCH_SUCCESS,
                profilePictureService.getProfilePictureUrl(userId),
                FieldConstantValue.PROFILE_PICTURE_URL);
    }

    /** What the picture picker renders: the current picture plus every linked provider's avatar. */
    @GetMapping("/me/profile-picture/options")
    public ResponseEntity<GlobalApiResponse<ProfilePictureOptionsDto>> getProfilePictureOptions() {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return ok(SuccessResponseConstant.FETCH_SUCCESS,
                profilePictureService.getOptions(userId),
                FieldConstantValue.PROFILE_PICTURE_OPTIONS);
    }

    /**
     * Adopts a linked provider's avatar. The bytes are copied into our own storage, so the
     * picture keeps working after that provider is unlinked or its CDN link expires.
     */
    @PutMapping("/me/profile-picture/from-identity/{identityId}")
    public ResponseEntity<GlobalApiResponse<ProfilePictureDto>> selectProviderPicture(@PathVariable UUID identityId) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return ok(SuccessResponseConstant.PROFILE_PICTURE_UPDATED,
                profilePictureService.selectFromIdentity(userId, identityId));
    }

    @PostMapping("/me/profile-picture")
    public ResponseEntity<GlobalApiResponse<ProfilePictureDto>> uploadProfilePicture(@RequestParam("file") MultipartFile file) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return ok(SuccessResponseConstant.PROFILE_PICTURE_UPDATED,
                profilePictureService.upload(userId, file));
    }

    @DeleteMapping("/me/profile-picture")
    public ResponseEntity<GlobalApiResponse<Void>> removeProfilePicture() {
        profilePictureService.remove(jwtTokenUtil.getCurrentUserId());
        return ok(SuccessResponseConstant.PROFILE_PICTURE_REMOVED, null);
    }
}

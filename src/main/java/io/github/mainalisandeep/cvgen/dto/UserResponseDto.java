package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

    private UUID userId;

    private String email;

    private String name;

    private LocalDateTime createdAt;

    private Boolean isEmailVerified;

    /** Ready to use in {@code <img src>}; null when the user has no picture set. */
    private String profilePictureUrl;

    @Builder.Default
    private List<String> providers = new ArrayList<>();

    private UserRole role;

    private UserStatus status;

    private Integer creditBalance;
}

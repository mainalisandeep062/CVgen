package io.github.mainalisandeep.cvgen.dto;

import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin edit of an account. Every field is optional; null means "leave as is".
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminUserUpdateRequestDto {

    private UserRole role;

    private UserStatus status;

    @Size(min = 1, max = 255, message = "{validation.admin.user.name.size}")
    private String name;
}

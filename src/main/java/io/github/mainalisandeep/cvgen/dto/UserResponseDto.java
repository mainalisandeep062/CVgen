package io.github.mainalisandeep.cvgen.dto;

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

    private List<String> providers = new ArrayList<>();
}

package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.UserResponseDto;

import java.util.UUID;

public interface UserService {
    UserResponseDto getUserById(UUID userId);
}

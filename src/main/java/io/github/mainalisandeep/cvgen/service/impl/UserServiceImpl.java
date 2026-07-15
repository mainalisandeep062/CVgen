package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.dto.UserResponseDto;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.repository.UserIdentityRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    @Override
    public UserResponseDto getUserById(UUID userId) {
        User user = userRepository.findByUserId(userId);
        List<UserIdentity> userIdentity = userIdentityRepository.findByUserId(userId);

        UserResponseDto response =  UserResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .createdAt(
                        LocalDateTime.ofInstant(
                                user.getCreatedAt(),
                                ZoneId.systemDefault()
                        )
                )
                .isEmailVerified(user.isEmailVerified())
                .providers(userIdentity.stream().map(UserIdentity::getProvider).toList())
                .build();

        return response;
    }
}

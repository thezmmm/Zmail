package com.zmail.controller;

import com.zmail.model.ApiResponse;
import com.zmail.model.User;
import com.zmail.model.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(ApiResponse.ok(toDto(user))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Session invalid: user no longer exists")));
    }

    private UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getName(), u.getCreatedAt());
    }

    record UserDto(UUID id, String email, String name, OffsetDateTime createdAt) {}
}

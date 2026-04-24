package com.datacrowd.auth.api.internal;

import com.datacrowd.auth.user.UserEntity;
import com.datacrowd.auth.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/users")
public class InternalUsersController {

    private final UserRepository userRepository;

    public InternalUsersController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    @PatchMapping("/{userId}/role")
    public Map<String, String> changeRole(
            @PathVariable UUID userId,
            @RequestBody RoleChangeBody body) {

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + userId));

        user.setRole(body.role);
        userRepository.save(user);

        return Map.of(
                "status", "ok",
                "userId", userId.toString(),
                "newRole", body.role
        );
    }

    // Простой record для тела запроса
    public record RoleChangeBody(
            @NotBlank
            @Pattern(regexp = "^(WORKER|CLIENT|ADMIN|REVIEWER)$")
            String role
    ) {}
}
package com.metacoding.user.web;

import com.metacoding.user.domain.User;
import com.metacoding.user.repository.UserRepository;
import com.metacoding.user.util.JwtProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    // 로그인: username + password 확인 → JWT 발급
    @PostMapping("/api/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        User user = userRepository.findByUsernameAndPassword(req.getUsername(), req.getPassword())
                .orElseThrow(() -> new RuntimeException("로그인 실패"));

        String token = jwtProvider.create(user.getId(), user.getUsername());

        return Map.of(
                "token", token,
                "userId", user.getId(),
                "username", user.getUsername()
        );
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}

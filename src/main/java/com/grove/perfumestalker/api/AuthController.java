package com.grove.perfumestalker.api;

import com.grove.perfumestalker.dto.UserAccountCommand;
import com.grove.perfumestalker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody UserAccountCommand request) {
        return userService.login(request)
                .map(token -> ResponseEntity.ok(Map.of("token", token)))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(Map.of("error", e.getMessage()))));
    }

    @PostMapping("/signup")
    public Mono<ResponseEntity<Map<String, String>>> signup(@RequestBody UserAccountCommand request) {
        return userService.signup(request)
                .map(msg -> ResponseEntity.ok(Map.of("message", msg)))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(Map.of("error", e.getMessage()))));
    }

}
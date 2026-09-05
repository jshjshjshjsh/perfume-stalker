package com.grove.perfumestalker.api;

import com.grove.perfumestalker.dto.UserResponse;
import com.grove.perfumestalker.dto.UserUpdateRequest;
import com.grove.perfumestalker.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public Mono<ResponseEntity<UserResponse>> getMyInfo(@RequestAttribute("userPageId") String userPageId) {
        return userService.getUserInfo(userPageId).map(ResponseEntity::ok);
    }

    @PatchMapping("/me")
    public Mono<ResponseEntity<String>> updateMyInfo(@RequestAttribute("userPageId") String userPageId,
                                                     @RequestBody UserUpdateRequest request) {
        return userService.updateUserInfo(userPageId, request)
                .then(Mono.just(ResponseEntity.ok("✅ 정보 수정 완료!")));
    }
}
package com.grove.perfumestalker.api;

import com.grove.perfumestalker.dto.WishlistRequest;
import com.grove.perfumestalker.dto.WishlistResponse;
import com.grove.perfumestalker.notion.NotionWishlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final NotionWishlistService wishlistService;

    @GetMapping
    public Mono<ResponseEntity<List<WishlistResponse>>> getWishlist(
            @RequestAttribute("userPageId") String userPageId) {
        return wishlistService.getWishlist(userPageId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(List.of()));
    }

    @PostMapping
    public Mono<ResponseEntity<String>> addWishlist(
            @RequestBody WishlistRequest request,
            @RequestAttribute("userPageId") String userPageId) {
        return wishlistService.addWishlist(request, userPageId)
                .map(id -> ResponseEntity.ok("✅ 위시리스트 추가 완료: " + id));
    }

    @DeleteMapping("/{pageId}")
    public Mono<ResponseEntity<String>> deleteWishlist(
            @PathVariable String pageId) {
        return wishlistService.deleteWishlist(pageId)
                .then(Mono.just(ResponseEntity.ok("🗑️ 위시리스트 삭제 완료")));
    }

    @PutMapping("/reorder")
    public Mono<ResponseEntity<String>> reorderWishlist(@RequestBody List<String> pageIds) {
        return wishlistService.reorderWishlist(pageIds)
                .then(Mono.just(ResponseEntity.ok("✅ 정렬 순서 저장 완료")));
    }

    @PatchMapping("/{pageId}")
    public Mono<ResponseEntity<String>> updateWishlist(@PathVariable String pageId, @RequestBody WishlistRequest request) {
        return wishlistService.updateWishlist(pageId, request)
                .then(Mono.just(ResponseEntity.ok("✅ 위시 수정 완료")));
    }
}
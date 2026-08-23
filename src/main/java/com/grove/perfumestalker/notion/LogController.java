package com.grove.perfumestalker.notion;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {

    private final NotionService notionService;
    private final NotionLogService notionLogService;

    /**
     * 프론트엔드(모바일 웹)에서 NFC UID를 받아 착향 로그를 기록하는 엔드포인트
     */
    @PostMapping("/scan")
    public Mono<ResponseEntity<String>> recordUsageLog(@RequestBody ScanRequest request) {
        String uid = request.getUid();
        log.info("📱 [Perfume Stalker] NFC 태그 스캔 요청 수신: UID = {}", uid);

        // 1. UID로 향수 마스터 페이지 ID 조회 -> 2. 로그 Insert (flatMap으로 체이닝)
        return notionService.findPerfumePageIdByUid(uid)
                .flatMap(pageId -> notionLogService.createUsageLog(pageId))
                .map(v -> ResponseEntity.ok("✅ 착향 로그가 성공적으로 기록되었습니다."))
                .onErrorResume(e -> {
                    log.error("❌ 착향 로그 기록 중 에러 발생: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body("실패: " + e.getMessage()));
                });
    }

    // API 요청 파라미터 DTO
    @Data
    public static class ScanRequest {
        private String uid;
    }
}
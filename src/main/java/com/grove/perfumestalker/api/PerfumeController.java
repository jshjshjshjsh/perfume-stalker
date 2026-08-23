package com.grove.perfumestalker.api;

import com.grove.perfumestalker.notion.NotionLogService;
import com.grove.perfumestalker.notion.NotionService;
import com.grove.perfumestalker.dto.PerfumeRegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/perfumes")
@RequiredArgsConstructor
public class PerfumeController {

    private final NotionService notionService;
    private final NotionLogService notionLogService;

    /**
     * 프론트엔드에서 향수 등록 폼을 제출하면 호출됨
     * 1. 마스터 DB에 Insert -> 2. 착향 로그 DB에 자동 Insert
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<String>> registerNewPerfume(@RequestBody PerfumeRegisterRequest request) {
        log.info("📱 [Perfume Stalker] 새 향수 등록 요청: {}", request.getName());

        return notionService.createPerfumeMaster(request)
                // 방금 만든 향수 Page ID를 받아서, 다시 스캔할 필요 없이 곧바로 착향 로그 기록!
                .flatMap(pageId -> notionLogService.createUsageLog(pageId))
                .map(v -> ResponseEntity.ok("✅ 향수 등록 및 착향 로그 기록이 완료되었습니다."))
                .onErrorResume(e -> {
                    log.error("❌ 향수 등록 파이프라인 에러: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body("실패: " + e.getMessage()));
                });
    }
}
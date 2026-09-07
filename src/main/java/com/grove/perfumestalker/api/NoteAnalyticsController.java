package com.grove.perfumestalker.api;

import com.grove.perfumestalker.analytics.NoteAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class NoteAnalyticsController {

    private final NoteAnalyticsService noteAnalyticsService;

    @GetMapping("/notes")
    public Mono<NoteAnalyticsService.NoteAnalyticsResponse> getNoteAnalytics(
            @RequestParam String userId) {

        // 노션 유저 페이지 ID (또는 DB 관계형 ID)를 파라미터로 받아서 실행
        return noteAnalyticsService.analyzeUserNotes(userId);
    }
}
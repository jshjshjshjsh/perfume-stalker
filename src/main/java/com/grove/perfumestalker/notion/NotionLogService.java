package com.grove.perfumestalker.notion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionLogService {

    private final WebClient notionWebClient;

    @Value("${notion.db.usage-log-id}")
    private String usageLogDbId;

    /**
     * 향수 마스터의 Page ID를 받아 착향 로그 DB에 새로운 기록을 비동기로 Insert
     */
    public Mono<Void> createUsageLog(String masterPageId) {
        // 현재 한국 시간 기준 ISO-8601 포맷 생성 (예: 2026-08-22T22:49:00+09:00)
        String nowIso = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // 고유 LOG_ID 생성 (예: LOG-8a2b3c4d)
        String logId = "LOG-" + UUID.randomUUID().toString().substring(0, 8);

        // 노션 API Insert JSON 페이로드 구조
        Map<String, Object> body = Map.of(
                "parent", Map.of("database_id", usageLogDbId),
                "properties", Map.of(
                        "LOG_ID", Map.of(
                                "title", List.of(
                                        Map.of("text", Map.of("content", logId))
                                )
                        ),
                        "PERFUME", Map.of(
                                "relation", List.of(
                                        Map.of("id", masterPageId) // MASTER DB에서 찾은 향수 페이지 ID로 관계 엮기
                                )
                        ),
                        "DATE", Map.of(
                                "date", Map.of("start", nowIso)
                        )
                )
        );

        return notionWebClient.post()
                .uri("/pages") // 데이터를 생성할 때는 /pages를 호출함
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("✅ 노션 착향 로그 기록 완료: {}", logId))
                .doOnError(e -> log.error("❌ 노션 착향 로그 기록 실패", e));
    }
}
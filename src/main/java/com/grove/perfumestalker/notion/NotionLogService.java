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

    // 2026 스펙 방어용: 하이픈 없는 ID가 들어와도 무조건 표준 UUID(8-4-4-4-12)로 변환
    private String formatUuid(String id) {
        String cleanId = id.trim().replace("-", "");
        if (cleanId.length() != 32) return id;
        return cleanId.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"
        );
    }

    public Mono<Void> createUsageLog(String masterPageId) {
        String nowIso = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String logId = "LOG-" + UUID.randomUUID().toString().substring(0, 8);

        // 부모 DB ID 포맷팅
        String formattedUsageLogDbId = formatUuid(usageLogDbId);
        // 관계형으로 엮을 마스터 향수 Page ID 포맷팅 (이것도 2026 스펙에선 하이픈 필수)
        String formattedMasterPageId = formatUuid(masterPageId);

        Map<String, Object> body = Map.of(
                "parent", Map.of(
                        "type", "database_id", // 페이지 생성 시에는 database_id 사용
                        "database_id", formattedUsageLogDbId
                ),
                "properties", Map.of(
                        "LOG_ID", Map.of(
                                "title", List.of(
                                        Map.of("text", Map.of("content", logId))
                                )
                        ),
                        "PERFUME", Map.of(
                                "relation", List.of(
                                        Map.of("id", formattedMasterPageId)
                                )
                        ),
                        "DATE", Map.of(
                                "date", Map.of("start", nowIso)
                        )
                )
        );

        return notionWebClient.post()
                .uri("/pages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("✅ 노션 착향 로그 기록 완료: {}", logId))
                .doOnError(e -> {
                    if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        String errorBody = ((org.springframework.web.reactive.function.client.WebClientResponseException) e).getResponseBodyAsString();
                        log.error("❌ 노션 API 400/404 에러 상세: {}", errorBody);
                    } else {
                        log.error("❌ 착향 로그 기록 실패", e);
                    }
                });
    }
}
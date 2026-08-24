package com.grove.perfumestalker.notion;

import com.grove.perfumestalker.enums.NotionUsageLog;
import com.grove.perfumestalker.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionLogService {

    private final WebClient notionWebClient;
    private final NotionModule notionModule;

    @Value("${notion.db.usage-log-id}")
    private String usageLogDbId;

    public Mono<Void> createUsageLog(String masterPageId, WeatherService.WeatherData weather) {
        String nowIso = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String logId = "LOG-" + UUID.randomUUID().toString().substring(0, 8);

        String formattedUsageLogDbId = notionModule.formatUuid(usageLogDbId);
        String formattedMasterPageId = notionModule.formatUuid(masterPageId);

        Map<String, Object> properties = new HashMap<>();
        properties.put(NotionUsageLog.LOG_ID.getColumnName(), NotionUsageLog.LOG_ID.formatValue(logId));
        properties.put(NotionUsageLog.PERFUME.getColumnName(), NotionUsageLog.PERFUME.formatValue(formattedMasterPageId));
        properties.put(NotionUsageLog.DATE.getColumnName(), NotionUsageLog.DATE.formatValue(nowIso));
        properties.put(NotionUsageLog.WEATHER.getColumnName(), NotionUsageLog.WEATHER.formatValue(weather.weather()));
        properties.put(NotionUsageLog.TEMPERATURE.getColumnName(), NotionUsageLog.TEMPERATURE.formatValue(weather.temperature()));
        properties.put(NotionUsageLog.HUMIDITY.getColumnName(), NotionUsageLog.HUMIDITY.formatValue(weather.humidity()));

        Map<String, Object> body = Map.of(
                "parent", Map.of("type", "database_id", "database_id", formattedUsageLogDbId),
                "properties", properties
        );

        return notionWebClient.post()
                .uri("/pages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("✅ 노션 착향 로그 기록 완료: {}", logId))
                .doOnError(this::handleNotionError);
    }

    private void handleNotionError(Throwable e) {
        if (e instanceof WebClientResponseException ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.error("❌ 노션 API 40x/50x 에러 상세: {}", errorBody);
        } else {
            log.error("❌ 노션 착향 로그 기록 실패", e);
        }
    }
}
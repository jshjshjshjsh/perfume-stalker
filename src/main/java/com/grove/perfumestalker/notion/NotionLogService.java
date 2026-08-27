package com.grove.perfumestalker.notion;

import com.grove.perfumestalker.dto.LogUpdateRequest;
import com.grove.perfumestalker.enums.NotionUsageLog;
import com.grove.perfumestalker.notion.util.NotionParserUtils;
import com.grove.perfumestalker.notion.util.NotionTokenUtils;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionLogService {

    private final WebClient notionWebClient;
    private final NotionTokenUtils notionTokenUtils;

    @Value("${notion.db.usage-log-id}")
    private String usageLogDbId;
    @Value("${notion.usage-log-data-source-id}")
    private String usageLogDataSourceId;

    public Mono<Void> createUsageLog(String masterPageId, WeatherService.WeatherData weather) {
        String nowIso = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String logId = "LOG-" + UUID.randomUUID().toString().substring(0, 8);

        String formattedUsageLogDbId = notionTokenUtils.formatUuid(usageLogDbId);
        String formattedMasterPageId = notionTokenUtils.formatUuid(masterPageId);

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
            log.error("❌ 노션 API 40x/50x 에러 상세: {}", ex.getResponseBodyAsString());
        } else {
            log.error("❌ 노션 착향 로그 기록 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, String>>> getRecentLogs(int limit) {
        Map<String, Object> queryBody = Map.of(
                "page_size", limit,
                "sorts", List.of(Map.of("timestamp", "created_time", "direction", "descending"))
        );

        String formattedDbId = notionTokenUtils.formatUuid(usageLogDataSourceId);

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDbId)
                .bodyValue(queryBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                        response.bodyToMono(String.class).flatMap(error -> {
                            log.error("🚨 노션 4xx 에러: {}", error);
                            return Mono.error(new RuntimeException("Notion API 4xx Error"));
                        })
                )
                .bodyToMono(Map.class)
                .map(response -> {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    if (results == null || results.isEmpty()) return List.<Map<String, String>>of();

                    return results.stream().map(page -> {
                        Map<String, Object> props = (Map<String, Object>) page.get("properties");

                        String pageId = (String) page.get("id");
                        String date = NotionParserUtils.extractDate(props, NotionUsageLog.DATE.name());
                        String weather = NotionParserUtils.extractSelect(props, NotionUsageLog.WEATHER.name());
                        String perfumeName = NotionParserUtils.extractPerfumeName(props, NotionUsageLog.PERFUME_ROLLUP.getColumnName());
                        String imageUrl = NotionParserUtils.extractRollupImage(props, NotionUsageLog.IMAGE_ROLLUP.getColumnName());
                        String temp = NotionParserUtils.extractNumber(props, NotionUsageLog.TEMPERATURE.getColumnName());
                        String humidity = NotionParserUtils.extractNumber(props, NotionUsageLog.HUMIDITY.getColumnName());

                        return Map.of(
                                "pageId", pageId,
                                "date", date,
                                "perfumeName", perfumeName.isEmpty() ? "Unknown" : perfumeName,
                                "imageUrl", imageUrl,
                                "weather", weather,
                                "temp", temp,
                                "humidity", humidity
                        );
                    }).collect(Collectors.toList());
                })
                .onErrorResume(e -> {
                    log.error("❌ 최근 로그 조회 에러: ", e);
                    return Mono.just(List.of());
                });
    }

    public Mono<Void> updateUsageLog(String pageId, LogUpdateRequest request) {
        Map<String, Object> properties = new HashMap<>();

        if (request.getWeather() != null) {
            properties.put(NotionUsageLog.WEATHER.getColumnName(), NotionUsageLog.WEATHER.formatValue(request.getWeather()));
        }
        if (request.getTemp() != null) {
            properties.put(NotionUsageLog.TEMPERATURE.getColumnName(), NotionUsageLog.TEMPERATURE.formatValue(request.getTemp()));
        }
        if (request.getHumidity() != null) {
            properties.put(NotionUsageLog.HUMIDITY.getColumnName(), NotionUsageLog.HUMIDITY.formatValue(request.getHumidity()));
        }

        return notionWebClient.patch()
                .uri("/pages/{pageId}", pageId)
                .bodyValue(Map.of("properties", properties))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("✅ 노션 착향 로그 수정 완료: {}", pageId))
                .doOnError(this::handleNotionError);
    }

    public Mono<Void> deleteUsageLog(String pageId) {
        return notionWebClient.patch() // 노션 삭제는 사실상 상태 업데이트(PATCH)임
                .uri("/pages/{pageId}", pageId)
                .bodyValue(Map.of("in_trash", true))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("🗑️ 노션 착향 로그 삭제 완료: {}", pageId))
                .doOnError(this::handleNotionError);
    }
}
package com.grove.perfumestalker.notion;

import com.grove.perfumestalker.dto.LogAnalyticsDto;
import com.grove.perfumestalker.dto.LogUpdateRequest;
import com.grove.perfumestalker.dto.UsageLogCreateCommand;
import com.grove.perfumestalker.dto.UsageLogResponse;
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
import java.util.*;
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

    public Mono<List<UsageLogResponse>> getLogsByPerfume(String perfumeId, String userPageId) {
        String formattedDbId = notionTokenUtils.formatUuid(usageLogDataSourceId);

        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "and", List.of(
                                Map.of("property", NotionUsageLog.USER.getColumnName(),
                                        "relation", Map.of("contains", userPageId)),
                                Map.of("property", NotionUsageLog.PERFUME.getColumnName(),
                                        "relation", Map.of("contains", perfumeId))
                        )
                ),
                "sorts", List.of(
                        Map.of("property", NotionUsageLog.DATE.getColumnName(),
                                "direction", "descending")
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDbId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    if (results == null) return List.of();

                    return results.stream().map(page -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> props = (Map<String, Object>) page.get("properties");

                        return new UsageLogResponse(
                                (String) page.get("id"),
                                NotionParserUtils.extractPerfumeName(props, NotionUsageLog.PERFUME.getColumnName()),
                                NotionParserUtils.extractDate(props, NotionUsageLog.DATE.getColumnName()),
                                NotionParserUtils.extractSelect(props, NotionUsageLog.WEATHER.getColumnName()),
                                Double.valueOf(NotionParserUtils.extractNumber(props, NotionUsageLog.TEMPERATURE.getColumnName()).isEmpty() ? "0" : NotionParserUtils.extractNumber(props, NotionUsageLog.TEMPERATURE.getColumnName())),
                                Double.valueOf(NotionParserUtils.extractNumber(props, NotionUsageLog.HUMIDITY.getColumnName()).isEmpty() ? "0" : NotionParserUtils.extractNumber(props, NotionUsageLog.HUMIDITY.getColumnName())),
                                NotionParserUtils.extractRollupImage(props, NotionUsageLog.IMAGE_ROLLUP.getColumnName()),
                                NotionParserUtils.extractNumber(props, NotionUsageLog.RATE.getColumnName()).isEmpty() ? null : Double.valueOf(NotionParserUtils.extractNumber(props, NotionUsageLog.RATE.getColumnName())),
                                NotionParserUtils.extractRichText(props, NotionUsageLog.COMMENT.getColumnName())
                        );
                    }).collect(Collectors.toList());
                });
    }

    public Mono<Void> createUsageLog(UsageLogCreateCommand command, String userPageId) {

        // 커스텀 날짜가 없으면 현재 시간 사용 (ISO 8601 포맷)
        String logDate = (command.customDate() != null && !command.customDate().trim().isEmpty())
                ? command.customDate()
                : ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String logId = "LOG-" + UUID.randomUUID().toString().substring(0, 8);
        String formattedUsageLogDbId = notionTokenUtils.formatUuid(usageLogDbId);
        String formattedMasterPageId = notionTokenUtils.formatUuid(command.masterPageId());

        Map<String, Object> properties = new HashMap<>();
        properties.put(NotionUsageLog.LOG_ID.getColumnName(), NotionUsageLog.LOG_ID.formatValue(logId));
        properties.put(NotionUsageLog.PERFUME.getColumnName(), NotionUsageLog.PERFUME.formatValue(formattedMasterPageId));
        properties.put(NotionUsageLog.DATE.getColumnName(), NotionUsageLog.DATE.formatValue(logDate));

        // 💡 체크박스(useCurrentTemp)가 true면 현재 온도, false면 최고 온도(디폴트)
        Double selectedTemp = command.useCurrentTemp() ? command.weather().temperature() : command.weather().tempMax();
        if (command.weather().weather() != null && !command.weather().weather().equals("Unknown")) {
            properties.put(NotionUsageLog.WEATHER.getColumnName(), NotionUsageLog.WEATHER.formatValue(command.weather().weather()));
        }

        if (selectedTemp != null) {
            properties.put(NotionUsageLog.TEMPERATURE.getColumnName(), NotionUsageLog.TEMPERATURE.formatValue(selectedTemp));
        }

        if (command.weather().tempMin() != null) {
            properties.put(NotionUsageLog.TEMP_MIN.getColumnName(), NotionUsageLog.TEMP_MIN.formatValue(command.weather().tempMin()));
        }

        if (command.weather().tempMax() != null) {
            properties.put(NotionUsageLog.TEMP_MAX.getColumnName(), NotionUsageLog.TEMP_MAX.formatValue(command.weather().tempMax()));
        }

        if (command.weather().humidity() != null) {
            properties.put(NotionUsageLog.HUMIDITY.getColumnName(), NotionUsageLog.HUMIDITY.formatValue(command.weather().humidity()));
        }


        properties.put(NotionUsageLog.USER.getColumnName(), NotionUsageLog.USER.formatValue(userPageId));

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
    public Mono<List<Map<String, String>>> getRecentLogs(int limit, String userPageId) {
        Map<String, Object> queryBody = Map.of(
                "page_size", limit,
                "filter", Map.of(
                        "property", NotionUsageLog.USER.getColumnName(),
                        "relation", Map.of("contains", userPageId)
                ),
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
                        String rate = NotionParserUtils.extractNumber(props, NotionUsageLog.RATE.getColumnName());
                        String comment = NotionParserUtils.extractRichText(props, NotionUsageLog.COMMENT.getColumnName());

                        return Map.of(
                                "pageId", pageId,
                                "date", date,
                                "perfumeName", perfumeName.isEmpty() ? "Unknown" : perfumeName,
                                "imageUrl", imageUrl,
                                "weather", weather,
                                "temp", temp,
                                "humidity", humidity,
                                "rate", rate,
                                "comment", comment
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
        if (request.getRate() != null) {
            properties.put(NotionUsageLog.RATE.getColumnName(), NotionUsageLog.RATE.formatValue(request.getRate()));
        }
        if (request.getComment() != null) {
            properties.put(NotionUsageLog.COMMENT.getColumnName(), NotionUsageLog.COMMENT.formatValue(request.getComment()));
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

    public Mono<List<LogAnalyticsDto>> getAllLogsForAnalytics(String userPageId) {
        String formattedDbId = notionTokenUtils.formatUuid(usageLogDataSourceId);

        Map<String, Object> queryBody = Map.of(
                "filter", Map.of(
                        "and", List.of(
                                Map.of("property", NotionUsageLog.USER.getColumnName(),
                                        "relation", Map.of("contains", userPageId)),
                                // 💡 평점이 매겨진 완료된 로그만 가져옵니다.
                                Map.of("property", NotionUsageLog.RATE.getColumnName(),
                                        "number", Map.of("is_not_empty", true))
                        )
                )
        );

        return notionWebClient.post()
                .uri("/data_sources/{dbId}/query", formattedDbId)
                .bodyValue(queryBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    if (results == null || results.isEmpty()) return List.<LogAnalyticsDto>of();

                    return results.stream().map(page -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> props = (Map<String, Object>) page.get("properties");

                        double temp = Double.parseDouble(NotionParserUtils.extractNumber(props, NotionUsageLog.TEMPERATURE.getColumnName()).isEmpty() ? "0" : NotionParserUtils.extractNumber(props, NotionUsageLog.TEMPERATURE.getColumnName()));
                        double humidity = Double.parseDouble(NotionParserUtils.extractNumber(props, NotionUsageLog.HUMIDITY.getColumnName()).isEmpty() ? "0" : NotionParserUtils.extractNumber(props, NotionUsageLog.HUMIDITY.getColumnName()));
                        double rate = Double.parseDouble(NotionParserUtils.extractNumber(props, NotionUsageLog.RATE.getColumnName()));

                        // 💡 4계층 노트 롤업 파싱 (쉼표 기준으로 List 변환)
                        List<String> top = NotionParserUtils.parseRollupNotes(props, "Top Notes Rollup");
                        List<String> middle = NotionParserUtils.parseRollupNotes(props, "Middle Notes Rollup");
                        List<String> base = NotionParserUtils.parseRollupNotes(props, "Base Notes Rollup");
                        List<String> general = NotionParserUtils.parseRollupNotes(props, "General Notes Rollup");

                        return new LogAnalyticsDto(temp, humidity, rate, top, middle, base, general);
                    }).collect(Collectors.toList());
                })
                .onErrorResume(e -> {
                    log.error("❌ 통계용 로그 조회 에러: ", e);
                    return Mono.just(List.of());
                });
    }
}
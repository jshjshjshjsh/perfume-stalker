package com.grove.perfumestalker.api;

import com.grove.perfumestalker.dto.LogUpdateRequest;
import com.grove.perfumestalker.dto.ManualLogRequest;
import com.grove.perfumestalker.dto.UsageLogCreateCommand;
import com.grove.perfumestalker.notion.NotionLogService;
import com.grove.perfumestalker.notion.NotionService;
import com.grove.perfumestalker.user.UserService;
import com.grove.perfumestalker.weather.WeatherService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {

    private final NotionService notionService;
    private final NotionLogService notionLogService;
    private final WeatherService weatherService;
    private final UserService userService;
    public record ScanRequest(String uid, Double lat, Double lon) {}

    /**
     * 프론트엔드(모바일 웹)에서 NFC UID를 받아 착향 로그를 기록하는 엔드포인트
     */
    @PostMapping("/scan")
    public Mono<ResponseEntity<String>> scanNfcTag(@RequestBody ScanRequest request,
                                                   @RequestAttribute("userPageId") String userPageId) {
        log.info("📱 [Perfume Stalker] 스캔 수신: UID={}, GPS={},{}", request.uid(), request.lat(), request.lon());

        // 1. 위치 및 날씨 조회 로직 (GPS가 있으면 GPS 우선, 없으면 계정 DB 위치)
        Mono<WeatherService.WeatherData> weatherMono;
        if (request.lat() != null && request.lon() != null) {
            weatherMono = weatherService.getWeatherByCoordinates(request.lat(), request.lon());
        } else {
            weatherMono = userService.getDefaultLocation("jsh-admin")
                    .flatMap(weatherService::getWeatherByCity);
        }

        // 2. 향수 ID 조회와 날씨 조회를 동시에(Parallel) 실행하고 묶기!
        return Mono.zip(notionService.findPerfumePageIdByUid(request.uid()), weatherMono)
                .flatMap(tuple -> {
                    String perfumePageId = tuple.getT1();
                    WeatherService.WeatherData weather = tuple.getT2();
                    // 3. 묶인 데이터로 착향 로그 Insert
                    UsageLogCreateCommand command = new UsageLogCreateCommand(perfumePageId, weather, null);

                    return notionLogService.createUsageLog(command, userPageId);
                })
                .map(v -> ResponseEntity.ok("✅ 날씨 정보와 함께 착향 로그가 기록되었습니다."))
                .onErrorResume(e -> {
                    if (e instanceof IllegalArgumentException && e.getMessage().contains("Unregistered")) {
                        return Mono.just(ResponseEntity.badRequest().body("Unregistered NFC UID"));
                    }
                    log.error("❌ 에러: ", e);
                    return Mono.just(ResponseEntity.internalServerError().body("서버 에러 발생"));
                });
    }

    @PostMapping("/manual")
    public Mono<ResponseEntity<String>> createManualLog(@RequestBody ManualLogRequest request,
                                                        @RequestAttribute("userPageId") String userPageId) {
        log.info("📝 수동 착향 로그 등록 요청: {}", request.getPerfumeId());

        Mono<WeatherService.WeatherData> weatherMono = (request.getLat() != null && request.getLon() != null)
                ? weatherService.getWeatherByCoordinates(request.getLat(), request.getLon())
                : userService.getDefaultLocation("jsh-admin").flatMap(weatherService::getWeatherByCity);

        return weatherMono.flatMap(weather -> {

            // 💡 팩폭 해결: 수동 입력은 프론트에서 넘어온 커스텀 날짜(date)를 통째로 넣어서 포장!
            UsageLogCreateCommand command = new UsageLogCreateCommand(
                    request.getPerfumeId(),
                    weather,
                    request.getDate()
            );

            return notionLogService.createUsageLog(command, userPageId);

        }).then(Mono.just(ResponseEntity.ok("✅ 수동 등록 완료!")));
    }

    @GetMapping("/recent")
    public Mono<ResponseEntity<List<Map<String, String>>>> getRecentLogs(
            @RequestParam(defaultValue = "5") int limit,
            @RequestAttribute("userPageId") String userPageId) {
        return notionLogService.getRecentLogs(limit, userPageId)
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/{pageId}")
    public Mono<ResponseEntity<String>> updateLog(@PathVariable String pageId, @RequestBody LogUpdateRequest request) {
        return notionLogService.updateUsageLog(pageId, request)
                .then(Mono.just(ResponseEntity.ok("✅ 수정 완료!")));
    }

    @DeleteMapping("/{pageId}")
    public Mono<ResponseEntity<String>> deleteLog(@PathVariable String pageId) {
        return notionLogService.deleteUsageLog(pageId)
                .then(Mono.just(ResponseEntity.ok("🗑️ 삭제 완료!")));
    }

}
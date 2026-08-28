package com.grove.perfumestalker.api;

import com.grove.perfumestalker.crawling.CrawlingService;
import com.grove.perfumestalker.dto.UsageLogCreateCommand;
import com.grove.perfumestalker.notion.NotionLogService;
import com.grove.perfumestalker.notion.NotionService;
import com.grove.perfumestalker.dto.PerfumeRegisterRequest;
import com.grove.perfumestalker.user.UserService;
import com.grove.perfumestalker.weather.WeatherService;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/perfumes")
@RequiredArgsConstructor
public class PerfumeController {

    private final NotionService notionService;
    private final NotionLogService notionLogService;
    private final WeatherService weatherService;
    private final UserService userService;
    private final CrawlingService crawlingService;

    @PostMapping("/register")
    public Mono<ResponseEntity<String>> registerNewPerfume(@RequestBody PerfumeRegisterRequest request,
                                                           @RequestAttribute("userPageId") String userPageId) {
        log.info("📱 [최종 저장] 새 향수 등록 요청: {}", request.getName());

        // 1. 날씨 조회 로직 (크롤링 뺐으니 바로 날씨부터 찌름)
        Mono<WeatherService.WeatherData> weatherMono;
        if (request.getLat() != null && request.getLon() != null) {
            weatherMono = weatherService.getWeatherByCoordinates(request.getLat(), request.getLon());
        } else {
            weatherMono = userService.getDefaultLocation(userPageId)
                    .flatMap(weatherService::getWeatherByCity);
        }

        // 2. 향수 마스터 DB Insert (이제 request 안에 프론트가 확정한 이미지와 노트가 다 있음!)
        Mono<String> perfumeMasterMono = notionService.createPerfumeMaster(request, userPageId);

        // 3. Zip으로 묶어서 착향 로그 기록
        return Mono.zip(perfumeMasterMono, weatherMono)
                .flatMap(tuple -> {
                    String newPerfumePageId = tuple.getT1();
                    WeatherService.WeatherData weather = tuple.getT2();

                    UsageLogCreateCommand command = new UsageLogCreateCommand(newPerfumePageId, weather, null);
                    return notionLogService.createUsageLog(command, userPageId);
                })
                .map(v -> ResponseEntity.ok("✅ 향수 등록 및 날씨 정보가 포함된 착향 로그 기록 완료!"))
                .onErrorResume(e -> {
                    log.error("❌ 향수 등록 파이프라인 에러: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body("실패: " + e.getMessage()));
                });
    }

    /**
     * 1. 등록된 브랜드 목록 가져오기 (노션 DB 스키마 조회)
     */
    @GetMapping("/brands")
    public Mono<ResponseEntity<List<String>>> getBrands(@RequestAttribute("userPageId") String userPageId) {
        return notionService.getBrandOptions(userPageId)
                .map(ResponseEntity::ok);
    }

    /**
     * 2. 프레그런티카 크롤링 API
     */
    @GetMapping("/crawl")
    public Mono<ResponseEntity<Map<String, Object>>> crawlFragrantica(@RequestParam String url) {
        return crawlingService.crawl(url)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/list")
    public Mono<ResponseEntity<List<Map<String, String>>>> getPerfumeList(@RequestAttribute("userPageId") String userPageId) {
        return notionService.getAllPerfumes(userPageId)
                .map(ResponseEntity::ok);
    }
}
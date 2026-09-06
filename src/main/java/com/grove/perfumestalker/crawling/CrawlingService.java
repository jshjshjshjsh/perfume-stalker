package com.grove.perfumestalker.crawling;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class CrawlingService {

    private static final String SELECTOR_NOTE_LABEL = ".pyramid-note-label";
    private static final String SELECTOR_IMAGE = "img[itemprop='image']";
    private static final int TIMEOUT_MS = 25000;

    private Playwright playwright;
    private Browser browser;

    @PostConstruct
    public void init() {
        log.info("🚀 Playwright 브라우저 엔진 초기화 시작...");
        playwright = Playwright.create();

        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setChannel("chrome")
                .setIgnoreDefaultArgs(List.of("--enable-automation"))
                .setArgs(List.of(
                        "--disable-blink-features=AutomationControlled",
                        "--window-position=-32000,-32000",
                        "--window-size=1920,1080",
                        "--no-sandbox", // 💡 도커(리눅스) 환경 크롬 실행 필수 옵션 1
                        "--disable-dev-shm-usage" // 💡 도커 환경 크롬 실행 필수 옵션 2 (메모리 크래시 방지)
                )));

        log.info("✅ Playwright 브라우저 엔진 준비 완료 (Docker Xvfb + 진짜 크롬 모드)!");
    }

    @PreDestroy
    public void destroy() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        log.info("🛑 Playwright 브라우저 엔진 종료 완료.");
    }

    public Mono<Map<String, Object>> crawl(String url) {
        return Mono.fromCallable(() -> {
                    log.info("🌐 크롤링 시작: {}", url);

                    try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                            .setViewportSize(1920, 1080))) {

                        Page page = context.newPage();

                        page.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");
                        page.setDefaultNavigationTimeout(TIMEOUT_MS);

                        try {
                            // HTML 로딩 완료 시점에 즉시 파싱 대기 진입 (광고 로딩 무시)
                            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                            // CF 방어막 통과 3초 대기
                            page.waitForTimeout(3000);

                            page.waitForSelector(SELECTOR_NOTE_LABEL, new Page.WaitForSelectorOptions().setTimeout(15000));
                        } catch (Exception e) {
                            log.warn("⚠️ [디버그] 타임아웃 발생 (원인: {}), URL: {}", e.getMessage(), url);
                            throw new RuntimeException("CRAWL_TIMEOUT_FOR_RETRY");
                        }

                        String imageUrl = extractImageUrl(page);
                        Map<String, Object> notesData = extractNotesData(page);

                        log.info("✅ 크롤링 결과 - 이미지: [{}], 노트 추출 완료", !imageUrl.isEmpty() ? "성공" : "실패");

                        return Map.<String, Object>of(
                                "imageUrl", imageUrl,
                                "notes", notesData
                        );
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(2))
                        .filter(throwable -> "CRAWL_TIMEOUT_FOR_RETRY".equals(throwable.getMessage()))
                        .doBeforeRetry(retrySignal ->
                                log.info("🔄 클라우드플레어 우회 재시도 ({}회차): {}", retrySignal.totalRetries() + 1, url)
                        )
                )
                .onErrorResume(e -> {
                    log.error("❌ 최종 크롤링 실패: {}", e.getMessage());
                    return Mono.just(Map.of("imageUrl", "", "notes", Map.of()));
                });
    }

    private String extractImageUrl(Page page) {
        try {
            String url = page.getAttribute(SELECTOR_IMAGE, "src");
            return url != null ? url : "";
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractNotesData(Page page) {
        try {
            return (Map<String, Object>) page.evaluate("() => { " +
                    "let result = { top: [], middle: [], base: [], general: [] };" +
                    "let containers = document.querySelectorAll('.pyramid-level-container');" +

                    "if (containers.length === 0) {" +
                    "    let labels = document.querySelectorAll('.pyramid-note-label');" +
                    "    if (labels.length > 0) result.general = Array.from(labels).map(el => el.innerText.trim());" +
                    "    return result;" +
                    "}" +

                    "containers.forEach(container => {" +
                    "    let wrapper = container.closest('div[class*=\"mx-auto\"]') || container.parentElement.parentElement;" +
                    "    let h4 = wrapper ? wrapper.querySelector('h4') : null;" +
                    "    let headerText = h4 ? h4.innerText.toLowerCase() : '';" +
                    "    let notes = Array.from(container.querySelectorAll('.pyramid-note-label')).map(el => el.innerText.trim());" +

                    "    if (headerText.includes('top')) {" +
                    "        result.top = notes;" +
                    "    } else if (headerText.includes('middle') || headerText.includes('heart')) {" +
                    "        result.middle = notes;" +
                    "    } else if (headerText.includes('base')) {" +
                    "        result.base = notes;" +
                    "    } else {" +
                    "        result.general = result.general.concat(notes);" +
                    "    }" +
                    "});" +
                    "return result;" +
                    "}");
        } catch (Exception e) {
            return Map.of("top", List.of(), "middle", List.of(), "base", List.of(), "general", List.of());
        }
    }
}
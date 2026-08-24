package com.grove.perfumestalker.weather;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WeatherService {

    private final WebClient weatherWebClient;

    @Value("${openweathermap.api-key}")
    private String apiKey;

    public WeatherService() {
        // OpenWeatherMap 기본 URL 세팅
        this.weatherWebClient = WebClient.builder()
                .baseUrl("https://api.openweathermap.org/data/2.5")
                .build();
    }

    // 서비스 내부에서 쓸 깔끔한 DTO (Java 16+ Record 사용)
    public record WeatherData(String cityName, String weather, double temperature, double humidity) {}

    private record OpenWeatherResponse(List<Weather> weather, Main main, String name) {
        private record Weather(String main) {}
        private record Main(double temp, double humidity) {}
    }

    /**
     * 1. 도시 이름(예: "Busan")으로 날씨 조회
     */
    public Mono<WeatherData> getWeatherByCity(String city) {
        return weatherWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/weather")
                        .queryParam("q", city)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric") // 섭씨온도로 받기 위해 metric 필수
                        .build())
                .retrieve()
                .bodyToMono(OpenWeatherResponse.class)
                .map(this::convertToDto)
                .doOnError(e -> log.error("❌ 날씨 API (City) 호출 실패: {}", city, e));
    }

    /**
     * 2. GPS 좌표(위도, 경도)로 날씨 조회 (프론트엔드에서 넘어올 때 우선 적용)
     */
    public Mono<WeatherData> getWeatherByCoordinates(double lat, double lon) {
        return weatherWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/weather")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .build())
                .retrieve()
                .bodyToMono(OpenWeatherResponse.class)
                .map(this::convertToDto)
                .doOnError(e -> log.error("❌ 날씨 API (GPS) 호출 실패", e));
    }

    /**
     * OpenWeatherMap의 복잡한 JSON 응답에서 딱 필요한 온습도/날씨만 추출
     */
    private WeatherData convertToDto(OpenWeatherResponse res) {
        String mainWeather = res.weather().isEmpty() ? "Unknown" : res.weather().get(0).main();
        return new WeatherData(
                res.name(),
                mainWeather,
                res.main().temp(),
                res.main().humidity()
        );
    }

    private WeatherData parseWeatherData(Map<String, Object> response) {
        var main = (Map<String, Number>) response.get("main");
        var weatherArray = (List<Map<String, Object>>) response.get("weather");
        String mainWeather = (String) weatherArray.get(0).get("main");

        String cityName = (String) response.get("name");

        return new WeatherData(
                cityName,
                mainWeather,
                main.get("temp").doubleValue(),
                main.get("humidity").doubleValue()
        );
    }
}
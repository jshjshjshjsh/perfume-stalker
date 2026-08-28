package com.grove.perfumestalker.api;

import com.grove.perfumestalker.user.UserService;
import com.grove.perfumestalker.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final UserService userService;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getCurrentWeather(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestAttribute("userId") String userId) {

        // 1. 프론트에서 GPS를 넘겨준 경우 (Update 버튼 눌렀을 때)
        if (lat != null && lon != null) {
            String gpsLocName = String.format("GPS [%.2f, %.2f]", lat, lon);
            return weatherService.getWeatherByCoordinates(lat, lon)
                    .map(w -> ResponseEntity.ok(Map.of(
                            "location", w.cityName() + " (GPS)",
                            "weather", w.weather(),
                            "temp", w.temperature(),
                            "humidity", w.humidity()
                    )));
        }
        // 2. GPS가 없는 경우 (앱 초기 로딩 시)
        else {
            return userService.getDefaultLocation(userId)
                    .flatMap(loc -> weatherService.getWeatherByCity(loc)
                            .map(w -> ResponseEntity.ok(Map.of(
                                    "location", loc,
                                    "weather", w.weather(),
                                    "temp", w.temperature(),
                                    "humidity", w.humidity()
                            ))));
        }
    }
}
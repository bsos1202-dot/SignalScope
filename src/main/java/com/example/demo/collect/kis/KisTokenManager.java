package com.example.demo.collect.kis;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.example.demo.collect.kis.dto.KisTokenResponse;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisTokenManager {

    private final RestTemplate restTemplate;

    @Value("${api.kis.base-url}")
    private String baseUrl;

    @Value("${api.kis.app-key}")
    private String appKey;

    @Value("${api.kis.app-secret}")
    private String appSecret;

    // 멀티 스레드 환경에서 안전하게 토큰을 읽고 쓰기 위해 AtomicReference 사용
    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private final AtomicBoolean issueInProgress = new AtomicBoolean(false);

    /**
     * 서버 가동 시 최초 1회 실행하여 토큰을 발급받습니다.
     */
    @PostConstruct
    public void init() {
        log.info("[서버기동 시]초기 한국투자증권 Access Token 백그라운드 발급 시작");
        CompletableFuture.runAsync(this::issueTokenSafely);
    }

    /**
     * 스케줄러: 12시간(43,200,000ms)마다 주기적으로 실행되어 토큰을 갱신합니다.
     * KIS 토큰은 24시간 유효하므로 12시간마다 갱신하면 절대 만료되지 않습니다.
     */
    @Scheduled(fixedRate = 43200000)
    public void refreshToken() {
        log.info("[스케쥴]한국투자증권 Access Token 정기 갱신을 시작합니다.");
        issueTokenSafely();
    }

    /**
     * 실제 토큰 발급 API를 호출하는 내부 로직
     */
    private void issueTokenSafely() {
        if (!issueInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            issueToken();
        } finally {
            issueInProgress.set(false);
        }
    }

    private void issueToken() {
        String url = baseUrl + "/oauth2/tokenP";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // KIS API는 Body에 JSON 형태로 키를 요구합니다.
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("grant_type", "client_credentials");
        requestBody.put("appkey", appKey);
        requestBody.put("appsecret", appSecret);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        try {
            KisTokenResponse response = restTemplate.postForObject(url, entity, KisTokenResponse.class);
            if (response != null && response.accessToken() != null) {
                cachedToken.set(response.accessToken());
                log.info("한국투자증권 Access Token 발급/갱신 완료 - " + response.accessToken());
            }
        } catch (Exception e) {
            log.error("한국투자증권 Access Token 발급 중 오류 발생. (기존 토큰 유지)", e);
        }
    }

    /**
     * 다른 서비스(클라이언트)에서 캐싱된 토큰을 가져다 쓸 때 호출하는 메서드
     */
    public String getAccessToken() {
        if (cachedToken.get() == null) {
            issueTokenSafely();
        }
        return cachedToken.get();
    }
}
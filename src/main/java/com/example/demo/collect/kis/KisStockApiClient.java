package com.example.demo.collect.kis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.collect.kis.dto.KisStockResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisStockApiClient {

    private final RestTemplate restTemplate;

    @Value("${api.kis.base-url}")
    private String baseUrl;

    @Value("${api.kis.app-key}")
    private String appKey;

    @Value("${api.kis.app-secret}")
    private String appSecret;

    /**
     * 종목 코드를 받아 현재가, 등락률, 거래량 데이터를 조회합니다.
     */
    public KisStockResponse getCurrentPrice(String stockCd, String accessToken) {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J") // J: 주식, ETF, ETN
                .queryParam("FID_INPUT_ISCD", stockCd)    // 종목코드 (예: 005930)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        // 한국투자증권 필수 헤더 세팅
        headers.set("authorization", "Bearer " + accessToken); // 별도의 토큰 발급 API를 통해 얻은 값
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010100"); // 주식현재가 시세 조회 TR ID

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KisStockResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, KisStockResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("한국투자증권 시세 조회 API 호출 중 오류 발생", e);
            return null;
        }
    }
}
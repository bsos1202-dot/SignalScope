package com.example.demo.collect.kis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.collect.kis.dto.KisCcnlResponse;
import com.example.demo.collect.kis.dto.KisInvestmentIndicatorResponse;
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
     * 종목 코드를 받아 현재가, 등락률, 거래량 및 VALUATION·52주 대비 등을 조회합니다.
     */
    public KisStockResponse getCurrentPrice(String stockCd, String accessToken) {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J") // J: 주식, ETF, ETN
                .queryParam("FID_INPUT_ISCD", stockCd)    // 종목코드 (예: 005930)
                .toUriString();

        HttpEntity<String> entity = new HttpEntity<>(kisHeaders(accessToken, "FHKST01010100"));

        try {
            ResponseEntity<KisStockResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, KisStockResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("한국투자증권 시세 조회 API 호출 중 오류 발생", e);
            return null;
        }
    }

    /**
     * RSI 등 투자지표 (주식현재가 투자정보).
     */
    public KisInvestmentIndicatorResponse getInvestmentIndicator(String stockCd, String accessToken) {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-investment-indicator")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCd)
                .toUriString();

        HttpEntity<String> entity = new HttpEntity<>(kisHeaders(accessToken, "FHKST01010900"));

        try {
            ResponseEntity<KisInvestmentIndicatorResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, KisInvestmentIndicatorResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("한국투자증권 투자지표(RSI 등) 조회 실패", e);
            return null;
        }
    }

    /**
     * 시간대별 체결 (체결강도 등). 최신 체결은 배열 앞쪽에 옵니다.
     */
    public KisCcnlResponse getTimeConclusion(String stockCd, String accessToken) {
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-ccnl")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", stockCd)
                .toUriString();

        HttpEntity<String> entity = new HttpEntity<>(kisHeaders(accessToken, "FHKST01010300"));

        try {
            ResponseEntity<KisCcnlResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, KisCcnlResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("한국투자증권 체결(체결강도) 조회 실패", e);
            return null;
        }
    }

    private HttpHeaders kisHeaders(String accessToken, String trId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", "Bearer " + accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", trId);
        headers.set("custtype", "P");
        return headers;
    }
}
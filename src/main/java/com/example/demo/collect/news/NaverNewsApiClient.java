package com.example.demo.collect.news;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.collect.news.dto.NaverNewsResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverNewsApiClient {

    private final RestTemplate restTemplate;

    @Value("${api.naver.news-url:https://openapi.naver.com/v1/search/news.json}")
    private String newsUrl;

    @Value("${api.naver.client-id}")
    private String clientId;

    @Value("${api.naver.client-secret}")
    private String clientSecret;

    /**
     * 특정 키워드(예: "삼성전자 주가")로 최신 뉴스를 검색합니다.
     */
    public NaverNewsResponse searchNews(String keyword, int displayCount) {
    	// ⭐️ String URL 대신 URI 객체를 생성하며 명시적으로 UTF-8 인코딩을 수행합니다.
        URI uri = UriComponentsBuilder.fromUriString(newsUrl)
                .queryParam("query", keyword)
                .queryParam("display", displayCount)
                .queryParam("sort", "date")
                .build()
                .encode(StandardCharsets.UTF_8) // 한글 검색어 인코딩 처리
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // String URL 대신 인코딩이 완료된 URI 객체를 던집니다.
            ResponseEntity<NaverNewsResponse> response = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, NaverNewsResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("네이버 뉴스 API 호출 중 오류 발생", e);
            return null;
        }
    }
}
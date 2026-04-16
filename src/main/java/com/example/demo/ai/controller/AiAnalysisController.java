package com.example.demo.ai.controller;


import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ai.cache.AiTutorialFileCacheService;
import com.example.demo.ai.cache.dto.TutorialCacheFile;
import com.example.demo.ai.dto.AiTutorialResponse;
import com.example.demo.ai.openai.StockAiAnalysisService;
import com.example.demo.collect.dart.DartCorpCodeManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AiAnalysisController {

    private final StockAiAnalysisService aiAnalysisService;
    private final DartCorpCodeManager dartCorpCodeManager;
    private final AiTutorialFileCacheService tutorialFileCacheService;

    /**
     * AI 기반 WHY Approach 종목 튜토리얼 생성 API
     * 프론트엔드(검색 화면)에서 /ai/tutorial?corp_code=005930&corp_name=삼성전자 형태로 호출합니다.
     * 응답: {@link AiTutorialResponse} — 요약(summary)과 참조 근거(evidence).
     */
    @GetMapping(value = "/ai/tutorial", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAiTutorial(
            @RequestParam("corp_code") String stockCode, // 6자리 단축코드
            @RequestParam("corp_name") String corpName,
            @RequestParam(value = "market", defaultValue = "") String market) {
            
        log.info("AI 튜토리얼 분석 요청 수신 - 종목명: {}, 코드: {}", corpName, stockCode);

        // 1. 6자리 주식 코드로 8자리 DART 고유번호 매핑
        String dartCode = dartCorpCodeManager.getDartCode(stockCode);
        
        if (dartCode == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "DART 공시 조회가 지원되지 않는 비상장 종목이거나 코드가 잘못되었습니다."));
        }

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        Optional<TutorialCacheFile> cached = tutorialFileCacheService.readIfValid(stockCode.trim());
        if (cached.isPresent()) {
            TutorialCacheFile envelope = cached.get();
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-AIS-Tutorial-Cache", "HIT");
            headers.add("X-AIS-Tutorial-Cache-Expires", Instant.ofEpochMilli(envelope.expiresAtMillis()).toString());
            headers.add("X-AIS-Tutorial-Cache-Generated", Instant.ofEpochMilli(envelope.createdAtMillis()).toString());
            return ResponseEntity.ok().headers(headers).body(envelope.response());
        }

        AiTutorialResponse report = aiAnalysisService.generateWhyApproachTutorial(stockCode, dartCode, corpName, market, today);
        tutorialFileCacheService.put(stockCode.trim(), corpName, market, report);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-AIS-Tutorial-Cache", "MISS");
        long expiresAt = System.currentTimeMillis() + tutorialFileCacheService.getTtlMinutes() * 60_000L;
        headers.add("X-AIS-Tutorial-Cache-Expires", Instant.ofEpochMilli(expiresAt).toString());
        headers.add("X-AIS-Tutorial-Cache-Generated", Instant.now().toString());

        return ResponseEntity.ok().headers(headers).body(report);
    }
}
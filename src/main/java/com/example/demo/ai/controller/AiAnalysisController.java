package com.example.demo.ai.controller;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * AI 기반 WHY Approach 종목 튜토리얼 생성 API
     * 프론트엔드(검색 화면)에서 /ai/tutorial?corp_code=005930&corp_name=삼성전자 형태로 호출합니다.
     */
    @GetMapping("/ai/tutorial")
    public ResponseEntity<String> getAiTutorial(
            @RequestParam("corp_code") String stockCode, // 6자리 단축코드
            @RequestParam("corp_name") String corpName,
            @RequestParam(value = "market", defaultValue = "") String market) {
            
        log.info("AI 튜토리얼 분석 요청 수신 - 종목명: {}, 코드: {}", corpName, stockCode);

        // 1. 6자리 주식 코드로 8자리 DART 고유번호 매핑
        String dartCode = dartCorpCodeManager.getDartCode(stockCode);
        
        if (dartCode == null) {
            return ResponseEntity.badRequest().body("DART 공시 조회가 지원되지 않는 비상장 종목이거나 코드가 잘못되었습니다.");
        }

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 2. KIS용(6자리), DART용(8자리), 이름(뉴스검색용)을 각각 알맞게 분배하여 서비스 호출
        //String aiReport = aiAnalysisService.generateWhyApproachTutorial(stockCode, dartCode, corpName, today);
        String aiReport = aiAnalysisService.generateWhyApproachTutorial(stockCode, dartCode, corpName, market, today);

        return ResponseEntity.ok()
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body(aiReport);
    }
}
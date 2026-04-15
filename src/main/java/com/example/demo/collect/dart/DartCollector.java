package com.example.demo.collect.dart;

import org.springframework.stereotype.Service;

import com.example.demo.collect.core.ApiCollector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartCollector implements ApiCollector {

    private final DartApiClient dartApiClient;

    @Override
    public void collectData() {
        log.info("[{}] 수집 파이프라인 시작", getCollectorName());

        // 예시: 삼성전자(00126380)의 최근 공시 수집
        String corpCode = "00126380"; 
        String today = "20240327"; // 실제 운영 시에는 LocalDate.now() 활용

        try {
            // 1. 공시 검색 데이터 수집
           /* String disclosures = dartApiClient.searchDisclosures(corpCode, today, today);
            log.info("수집된 공시 데이터: {}", disclosures);
            
            // TODO: 수집된 JSON(disclosures)을 파싱하여 DB에 저장하거나 LLM 컨텍스트로 전송

            // 2. 재무 정보 데이터 수집 (예: 2023년 사업보고서)
            String financials = dartApiClient.getFinancialInfo(corpCode, "2023", "11011");
            log.info("수집된 재무 데이터: {}", financials);
            */
            // TODO: 재무 데이터 파싱 및 저장 로직 추가

        } catch (Exception e) {
            log.error("DART API 수집 중 오류 발생: ", e);
        }
    }

    @Override
    public String getCollectorName() {
        return "DART 공시/재무 데이터 수집기";
    }
}
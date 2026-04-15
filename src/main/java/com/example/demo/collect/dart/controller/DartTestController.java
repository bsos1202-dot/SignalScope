package com.example.demo.collect.dart.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.collect.dart.DartApiClient;
import com.example.demo.collect.dart.dto.DisclosureResponse;
import com.example.demo.collect.dart.dto.FinancialResponse;
import com.example.demo.collect.dart.dto.OwnershipResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DartTestController {

    private final DartApiClient dartApiClient;

    /**
     * 1. 공시검색 API 테스트
     * 예시: http://localhost:8080/collect/dart/searchDisclosures?corp_code=00126380&bgn_de=20240101&end_de=20240131
     */
    @GetMapping("/collect/dart/searchDisclosures")
    public ResponseEntity<DisclosureResponse> searchDisclosures(
            @RequestParam(name = "corp_code") String corpCode,
            @RequestParam(name = "bgn_de", required = false) String bgnDe,
            @RequestParam(name = "end_de", required = false) String endDe,
            @RequestParam(name = "page_no", defaultValue = "1") String pageNo,
            @RequestParam(name = "page_count", defaultValue = "10") String pageCount
    ) {
        log.info("공시검색 API 테스트 호출 - corpCode: {}", corpCode);
        DisclosureResponse response = dartApiClient.searchDisclosures(corpCode, bgnDe, endDe, pageNo, pageCount);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. 상장기업 재무정보 API 테스트
     * 예시: http://localhost:8080/collect/dart/financial?corp_code=00126380&bsns_year=2023
     */
    @GetMapping("/collect/dart/financial")
    public ResponseEntity<FinancialResponse> getFinancialInfo(
            @RequestParam("corp_code") String corpCode,
            @RequestParam(name = "bsns_year", defaultValue = "2023") String bsnsYear,
            @RequestParam(name = "reprt_code", defaultValue = "11011") String reprtCode) {
        log.info("재무정보 API 테스트 호출 - corpCode: {}, year: {}", corpCode, bsnsYear);
        FinancialResponse response = dartApiClient.getFinancialInfo(corpCode, bsnsYear, reprtCode);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. 지분공시 API 테스트
     * 예시: http://localhost:8080/collect/dart/ownership?corp_code=00126380
     */
    @GetMapping("/collect/dart/ownership")
    public ResponseEntity<OwnershipResponse> getOwnership(
            @RequestParam("corp_code") String corpCode) {
        log.info("지분공시 API 테스트 호출 - corpCode: {}", corpCode);
        OwnershipResponse response = dartApiClient.getOwnershipDisclosures(corpCode);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. 주요사항 보고서 API 테스트 (리스크 이벤트 파악)
     * 예시: http://localhost:8080/collect/dart/major-issues?corp_code=00126380&bgn_de=20240101&end_de=20240131
     */
    @GetMapping("/collect/dart/major-issues")
    public ResponseEntity<DisclosureResponse> getMajorIssues(
            @RequestParam("corp_code") String corpCode,
            @RequestParam("bgn_de") String bgnDe,
            @RequestParam("end_de") String endDe) {
        log.info("주요사항보고서 API 테스트 호출 - corpCode: {}", corpCode);
        // DisclosureResponse DTO를 재사용하여 결과를 반환합니다.
        DisclosureResponse response = dartApiClient.getMajorManagementIssues(corpCode, bgnDe, endDe);
        return ResponseEntity.ok(response);
    }

    /**
     * 5. 공시 상세 원문(ZIP) 다운로드 테스트
     * 예시: http://localhost:8080/collect/dart/document?rcept_no=20240308000798
     * (주의: 브라우저에서 접근 시 ZIP 파일이 자동으로 다운로드됩니다)
     */
    @GetMapping("/collect/dart/document")
    public ResponseEntity<byte[]> getDocument(@RequestParam("rcept_no") String rceptNo) {
        log.info("공시 상세 원문(ZIP) 다운로드 테스트 호출 - rceptNo: {}", rceptNo);
        byte[] zipData = dartApiClient.getDisclosureDocument(rceptNo);

        // 브라우저가 이 응답을 파일 다운로드로 인식하도록 헤더를 설정합니다.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "document_" + rceptNo + ".zip");

        return ResponseEntity.ok()
                .headers(headers)
                .body(zipData);
    }
}
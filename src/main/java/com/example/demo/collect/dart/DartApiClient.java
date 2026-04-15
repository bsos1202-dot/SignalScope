package com.example.demo.collect.dart;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.collect.dart.dto.DisclosureResponse;
import com.example.demo.collect.dart.dto.FinancialResponse;
import com.example.demo.collect.dart.dto.OwnershipResponse;
import com.example.demo.collect.dart.properties.DartProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; 

@Slf4j
@Component
@RequiredArgsConstructor
public class DartApiClient {

    private final RestTemplate restTemplate;
    private final DartProperties properties;

    public DisclosureResponse searchDisclosures(String corpCode, String beginDate, String endDate, String pageNo, String pageCount) {
        
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/list.json")
                .queryParam("crtfc_key", properties.key())
                .queryParam("corp_code", corpCode)
                .queryParam("page_no", pageNo)
                .queryParam("page_count", pageCount);

        if (beginDate != null && !beginDate.isEmpty()) {
            builder.queryParam("bgn_de", beginDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            builder.queryParam("end_de", endDate);
        }

        String url = builder.toUriString();
        log.info("DART {}", url.replace(properties.key(), "MASKED_KEY"));
        
        // getForObject의 두 번째 파라미터를 DTO 클래스로 변경합니다.
        return restTemplate.getForObject(url, DisclosureResponse.class);
    }
    
    /**
     * 2. 상장기업 재무정보 API
     */
    public FinancialResponse getFinancialInfo(String corpCode, String bsnsYear, String reprtCode) {
        String url = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/fnlttSinglAcnt.json")
                .queryParam("crtfc_key", properties.key())
                .queryParam("corp_code", corpCode)
                .queryParam("bsns_year", bsnsYear)
                .queryParam("reprt_code", reprtCode) // 11011(사업보고서), 11012(반기) 등
                .toUriString();
        return restTemplate.getForObject(url, FinancialResponse.class);
    }

    /**
     * 3. 지분공시 API (임원ㆍ주요주주 소유보고)
     */
    public OwnershipResponse getOwnershipDisclosures(String corpCode) {
    	String url = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/elestock.json")
                .queryParam("crtfc_key", properties.key())
                .queryParam("corp_code", corpCode)
                .toUriString();
        return restTemplate.getForObject(url, OwnershipResponse.class);
    }

    /**
     * 4. 주요사항 보고서 - DTO 적용 (DisclosureResponse 재사용)
     */
    public DisclosureResponse getMajorManagementIssues(String corpCode, String bgnDe, String endDe) {
        String url = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/list.json")
                .queryParam("crtfc_key", properties.key())
                .queryParam("corp_code", corpCode)
                .queryParam("bgn_de", bgnDe)
                .queryParam("end_de", endDe)
                .queryParam("pblntf_ty", "I") // 주요사항보고서만 필터링
                .toUriString();
        return restTemplate.getForObject(url, DisclosureResponse.class);
    }

    /**
     * 5. 공시 상세 원문 조회 (ZIP 파일이므로 byte[] 유지)
     */
    public byte[] getDisclosureDocument(String rceptNo) {
        String url = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/document.xml")
                .queryParam("crtfc_key", properties.key())
                .queryParam("rcept_no", rceptNo)
                .toUriString();
        return restTemplate.getForObject(url, byte[].class);
    }
}
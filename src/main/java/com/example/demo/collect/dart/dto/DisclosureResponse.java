package com.example.demo.collect.dart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 공시검색 응답
 * @author HWPS
 *
 */
public record DisclosureResponse(
        String status,
        String message,
        List<DisclosureItem> list
) {
    public record DisclosureItem(
            @JsonProperty("corp_code") String corpCode,
            @JsonProperty("corp_name") String corpName,
            @JsonProperty("report_nm") String reportNm, // ⭐️ 가장 중요한 보고서 제목
            @JsonProperty("rcept_dt") String rceptDt,
            @JsonProperty("rcept_no") String rceptNo
    ) {}
}

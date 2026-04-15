package com.example.demo.collect.dart.dto;

/**
 * 재무정보 응답
 * @author HWPS
 *
 */
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FinancialResponse(
        String status,
        String message,
        List<FinancialItem> list
) {
    public record FinancialItem(
            @JsonProperty("sj_nm") String sjNm,           // 재무제표 구분 (예: 재무상태표)
            @JsonProperty("account_nm") String accountNm, // 계정명 (예: 영업이익, 당기순이익)
            @JsonProperty("thstrm_amount") String currentAmount // 당기 금액
    ) {}
}
package com.example.demo.collect.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisStockResponse(
        @JsonProperty("rt_cd") String rtCd, // 0: 성공
        @JsonProperty("msg1") String msg1,  // 응답 메시지
        @JsonProperty("output") Output output // 실제 시세 데이터
) {
    public record Output(
            @JsonProperty("stck_prpr") String currentPrice, // 현재가
            @JsonProperty("prdy_ctrt") String changeRate,   // 전일 대비 등락률 (%)
            @JsonProperty("acml_vol") String volume         // 누적 거래량
    ) {}
}
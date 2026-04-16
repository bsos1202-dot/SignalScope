package com.example.demo.collect.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public record KisStockResponse(
        @JsonProperty("rt_cd") String rtCd, // 0: 성공
        @JsonProperty("msg1") String msg1,  // 응답 메시지
        @JsonProperty("output") Output output // 실제 시세 데이터
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            @JsonProperty("stck_prpr") String currentPrice, // 현재가
            @JsonProperty("prdy_ctrt") String changeRate,   // 전일 대비 등락률 (%)
            @JsonProperty("acml_vol") String volume,      // 누적 거래량
            @JsonProperty("per") String per,
            @JsonProperty("pbr") String pbr,
            @JsonProperty("eps") String eps,
            @JsonProperty("bps") String bps,
            @JsonProperty("vol_tnrt") String volumeTurnoverRate,
            @JsonProperty("w52_hgpr") String w52High,
            @JsonProperty("w52_lwpr") String w52Low,
            @JsonProperty("w52_hgpr_vrss_prpr_ctrt") String w52HighVsCurrentPct,
            @JsonProperty("w52_lwpr_vrss_prpr_ctrt") String w52LowVsCurrentPct,
            @JsonProperty("hts_frgn_ehrt") String foreignHoldingRatio,
            @JsonProperty("acml_tr_pbmn") String accumulatedTradeAmount,
            @JsonProperty("prdy_vrss_vol_rate") String volumeVsPrevDayRate
    ) {}
}
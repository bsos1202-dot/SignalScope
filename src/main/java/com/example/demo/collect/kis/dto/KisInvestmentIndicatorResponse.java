package com.example.demo.collect.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 주식현재가 투자정보 (RSI 등). TR: FHKST01010900
 */
public record KisInvestmentIndicatorResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") Output output
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            @JsonProperty("rsiv_val") String rsi
    ) {}
}

package com.example.demo.collect.kis.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 주식현재가 시간대별 체결 (체결강도 등). TR: FHKST01010300
 */
public record KisCcnlResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") List<OutputRow> output
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputRow(
            @JsonProperty("stck_cntg_hour") String contractHour,
            @JsonProperty("stck_prpr") String price,
            @JsonProperty("cntg_vol") String contractVolume,
            @JsonProperty("tday_rltv") String contractStrength,
            @JsonProperty("prdy_ctrt") String changeRate
    ) {}
}

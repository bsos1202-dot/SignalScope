package com.example.demo.collect.dart.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 지분공시 응답
 * @author HWPS
 *
 */
public record OwnershipResponse(
        String status,
        String message,
        List<OwnershipItem> list
) {
    public record OwnershipItem(
            @JsonProperty("repror") String repror,             // 보고자 (예: 이재용)
            @JsonProperty("isu_exs_itms_qyt") String ownedQty, // 소유 주식 수
            @JsonProperty("stkqy_flr_rt") String ownershipRate // 지분율 (%)
    ) {}
}

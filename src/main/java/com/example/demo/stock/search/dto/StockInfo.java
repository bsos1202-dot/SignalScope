package com.example.demo.stock.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockInfo {
    private String corpCode; // 단축코드 (예: 005930)
    private String corpName; // 종목명 (예: 삼성전자)
    private String market;   // 소속 시장 (예: 코스피, 코스닥)
}
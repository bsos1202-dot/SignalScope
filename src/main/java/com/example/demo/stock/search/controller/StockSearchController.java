package com.example.demo.stock.search.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.stock.search.StockCacheService;
import com.example.demo.stock.search.dto.StockInfo;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class StockSearchController {

    private final StockCacheService stockCacheService;

    @GetMapping("/api/stocks/all")
    public ResponseEntity<List<StockInfo>> getAllStocks() {
        // 서버의 메모리(List)에서 꺼내오므로 DB 조회나 File IO 없이 즉시 응답합니다.
        return ResponseEntity.ok(stockCacheService.getAllStocks());
    }
}
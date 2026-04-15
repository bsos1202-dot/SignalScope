package com.example.demo.collect.news.dto;

import java.util.List;

public record NaverNewsResponse(
        String lastBuildDate,
        int total,
        List<NewsItem> items
) {
    public record NewsItem(
            String title,       // 기사 제목
            String originallink,// 원본 링크
            String link,        // 네이버 뉴스 링크
            String description, // 기사 요약 (AI 분석 핵심 데이터)
            String pubDate      // 출고 시간
    ) {}
}
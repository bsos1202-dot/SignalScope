package com.example.demo.collect.hanwha.dto;

/**
 * 한화 WM 기업·산업분석 목록(1페이지) 한 행.
 */
public record HanwhaResearchListItem(
        String title,
        String link,
        String category,
        String author,
        String publishedAt,
        String snippet
) {}

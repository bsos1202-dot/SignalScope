package com.example.demo.collect.news.cache.dto;

import java.util.List;

import com.example.demo.collect.news.dto.NaverNewsResponse;
import com.example.demo.collect.news.dto.RssNewsItem;

/**
 * 시장 키워드(코스피/코스닥 등) 뉴스 스냅샷 — 디스크 캐시용.
 */
public record MarketNewsCacheEnvelope(
        int version,
        String marketSlug,
        long createdAtMillis,
        long expiresAtMillis,
        NaverNewsResponse naverMarket,
        List<RssNewsItem> googleMarket
) {
    public static final int CURRENT_VERSION = 1;

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }
}

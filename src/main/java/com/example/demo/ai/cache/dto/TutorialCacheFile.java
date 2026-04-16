package com.example.demo.ai.cache.dto;

import com.example.demo.ai.dto.AiTutorialResponse;

/**
 * 디스크에 저장하는 AI 튜토리얼 캐시 한 건의 래퍼.
 */
public record TutorialCacheFile(
        int version,
        String stockCode,
        String corpName,
        String market,
        long createdAtMillis,
        long expiresAtMillis,
        AiTutorialResponse response
) {
    public static final int CURRENT_VERSION = 1;

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }
}

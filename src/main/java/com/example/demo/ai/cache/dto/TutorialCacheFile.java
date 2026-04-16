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
    /** 응답 스키마 변경 시 올려 기존 캐시 파일을 무효화합니다 (예: TutorialEvidence 필드 추가). */
    public static final int CURRENT_VERSION = 3;

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }
}

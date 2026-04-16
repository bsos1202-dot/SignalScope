package com.example.demo.collect.hanwha.cache.dto;

import java.util.List;

import com.example.demo.collect.hanwha.dto.HanwhaResearchListItem;

/**
 * 한화 WM 리서치 목록 일 캐시 파일 본문.
 */
public record HanwhaResearchCacheEnvelope(int version, String cacheDay, List<HanwhaResearchListItem> items) {
    public static final int CURRENT_VERSION = 1;
}

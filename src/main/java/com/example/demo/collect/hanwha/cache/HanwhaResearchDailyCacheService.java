package com.example.demo.collect.hanwha.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.collect.hanwha.cache.dto.HanwhaResearchCacheEnvelope;
import com.example.demo.collect.hanwha.dto.HanwhaResearchListItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 한화 WM 리서치 목록(1페이지)을 KST 일자별 JSON으로 캐시합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HanwhaResearchDailyCacheService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper objectMapper;

    @Value("${app.hanwha-research.cache-directory:cache/hanwha-research}")
    private String cacheDirectory;

    @Value("${app.hanwha-research.cache-enabled:true}")
    private boolean cacheEnabled;

    public List<HanwhaResearchListItem> getOrLoadToday(Supplier<List<HanwhaResearchListItem>> onMiss) {
        if (!cacheEnabled) {
            return onMiss.get();
        }
        String day = LocalDate.now(KST).format(DAY);
        Path file = dayFile(day);
        try {
            if (Files.isRegularFile(file)) {
                HanwhaResearchCacheEnvelope env = objectMapper.readValue(file.toFile(), HanwhaResearchCacheEnvelope.class);
                if (env != null
                        && env.version() == HanwhaResearchCacheEnvelope.CURRENT_VERSION
                        && day.equals(env.cacheDay())
                        && env.items() != null) {
                    log.info("한화 WM 리서치 캐시 HIT: {} ({}건)", day, env.items().size());
                    return env.items();
                }
            }
        } catch (Exception e) {
            log.warn("한화 WM 리서치 캐시 읽기 실패 — 재수집: {}", file, e);
        }
        List<HanwhaResearchListItem> fresh = onMiss.get();
        if (fresh == null) {
            fresh = List.of();
        }
        try {
            Files.createDirectories(file.getParent());
            HanwhaResearchCacheEnvelope env = new HanwhaResearchCacheEnvelope(
                    HanwhaResearchCacheEnvelope.CURRENT_VERSION,
                    day,
                    fresh
            );
            objectMapper.writeValue(file.toFile(), env);
            log.info("한화 WM 리서치 캐시 저장: {} ({}건)", day, fresh.size());
        } catch (IOException e) {
            log.warn("한화 WM 리서치 캐시 저장 실패", e);
        }
        return fresh;
    }

    private Path dayFile(String day) {
        Path root = Path.of(cacheDirectory).toAbsolutePath().normalize();
        return root.resolve(day + ".json");
    }
}

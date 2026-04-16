package com.example.demo.collect.news.cache;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.collect.news.cache.dto.MarketNewsCacheEnvelope;
import com.example.demo.collect.news.dto.NaverNewsResponse;
import com.example.demo.collect.news.dto.RssNewsItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 코스피/코스닥 등 시장 키워드 뉴스(네이버·구글 RSS)를 시장 단위로 파일 캐시합니다.
 * 튜토리얼 종목 캐시와 분리되어 모든 종목이 동일 스냅샷을 공유합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketNewsFileCacheService {

    private static final Pattern SAFE_SLUG = Pattern.compile("^[A-Z0-9_-]{1,32}$");

    private final ObjectMapper objectMapper;

    @Value("${app.market-news-cache.directory:cache/market-news}")
    private String cacheDirectory;

    @Value("${app.market-news-cache.ttl-minutes:30}")
    private long ttlMinutes;

    @Value("${app.market-news-cache.enabled:true}")
    private boolean enabled;

    public Optional<MarketNewsCachedPayload> readIfValid(String marketLabel) {
        if (!enabled) {
            return Optional.empty();
        }
        String slug = toMarketSlug(marketLabel);
        if (slug == null) {
            return Optional.empty();
        }
        Path file = filePath(slug);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            MarketNewsCacheEnvelope env = objectMapper.readValue(bytes, MarketNewsCacheEnvelope.class);
            if (env == null || env.version() != MarketNewsCacheEnvelope.CURRENT_VERSION) {
                return Optional.empty();
            }
            if (env.isExpired()) {
                log.debug("시장 뉴스 캐시 만료 — 무시: {}", file);
                return Optional.empty();
            }
            log.info("시장 뉴스 캐시 HIT: {}", slug);
            return Optional.of(new MarketNewsCachedPayload(env.naverMarket(), env.googleMarket() != null ? env.googleMarket() : List.of()));
        } catch (Exception e) {
            log.warn("시장 뉴스 캐시 읽기 실패 — 무시: {}", file, e);
            return Optional.empty();
        }
    }

    public void put(String marketLabel, NaverNewsResponse naverMarket, List<RssNewsItem> googleMarket) {
        if (!enabled) {
            return;
        }
        String slug = toMarketSlug(marketLabel);
        if (slug == null) {
            return;
        }
        try {
            Path dir = ensureDirectory();
            long now = System.currentTimeMillis();
            long exp = now + ttlMinutes * 60_000L;
            MarketNewsCacheEnvelope env = new MarketNewsCacheEnvelope(
                    MarketNewsCacheEnvelope.CURRENT_VERSION,
                    slug,
                    now,
                    exp,
                    naverMarket,
                    googleMarket != null ? googleMarket : List.of()
            );
            byte[] json = objectMapper.writeValueAsBytes(env);
            Path target = dir.resolve(slug + ".json");
            Path tmp = dir.resolve(slug + ".json." + Thread.currentThread().getId() + ".tmp");
            Files.write(tmp, json);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(tmp);
            }
            log.info("시장 뉴스 캐시 저장: {} (만료 ~{}분 후)", slug, ttlMinutes);
        } catch (Exception e) {
            log.warn("시장 뉴스 캐시 저장 실패: {}", marketLabel, e);
        }
    }

    public record MarketNewsCachedPayload(NaverNewsResponse naverMarket, List<RssNewsItem> googleMarket) {}

    /**
     * 파일명용 슬러그. 알려진 시장명만 허용해 경로 조작을 막습니다.
     */
    public static String toMarketSlug(String market) {
        if (market == null) {
            return null;
        }
        String m = market.trim();
        if (m.isEmpty()) {
            return null;
        }
        if ("코스피".equals(m) || "KOSPI".equalsIgnoreCase(m)) {
            return "KOSPI";
        }
        if ("코스닥".equals(m) || "KOSDAQ".equalsIgnoreCase(m)) {
            return "KOSDAQ";
        }
        if ("코넥스".equals(m) || "KONEX".equalsIgnoreCase(m)) {
            return "KONEX";
        }
        String ascii = m.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "");
        if (ascii.isEmpty() || ascii.length() > 32 || !SAFE_SLUG.matcher(ascii).matches()) {
            return null;
        }
        return ascii;
    }

    private Path ensureDirectory() throws IOException {
        Path dir = Path.of(cacheDirectory).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }

    private Path filePath(String slug) {
        try {
            return ensureDirectory().resolve(slug + ".json");
        } catch (IOException e) {
            throw new IllegalStateException("시장 뉴스 캐시 디렉터리 생성 실패", e);
        }
    }
}

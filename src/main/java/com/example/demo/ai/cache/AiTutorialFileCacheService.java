package com.example.demo.ai.cache;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.ai.cache.dto.TutorialCacheFile;
import com.example.demo.ai.dto.AiTutorialResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 종목별 AI 튜토리얼 응답을 파일로 캐시합니다 (기본 TTL 10분).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTutorialFileCacheService {

    private static final Pattern STOCK_CODE = Pattern.compile("^\\d{6}$");

    private final ObjectMapper objectMapper;

    @Value("${app.tutorial-cache.directory:cache/tutorial}")
    private String cacheDirectory;

    @Value("${app.tutorial-cache.ttl-minutes:10}")
    private long ttlMinutes;

    /**
     * 유효한 캐시 래퍼가 있으면 반환합니다 (본문 + 만료 시각).
     */
    public Optional<TutorialCacheFile> readIfValid(String stockCode, boolean naverBoardScrapeEnabled) {
        if (!isValidCode(stockCode)) {
            return Optional.empty();
        }
        Path file = filePath(stockCode, naverBoardScrapeEnabled);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            TutorialCacheFile envelope = objectMapper.readValue(bytes, TutorialCacheFile.class);
            if (envelope == null || envelope.response() == null) {
                return Optional.empty();
            }
            if (envelope.version() != TutorialCacheFile.CURRENT_VERSION) {
                log.debug("튜토리얼 캐시 버전 불일치 — 무시: {} (파일 v{} 현재 v{})", file, envelope.version(), TutorialCacheFile.CURRENT_VERSION);
                return Optional.empty();
            }
            if (envelope.isExpired()) {
                log.debug("튜토리얼 캐시 만료됨 — 무시: {}", file);
                return Optional.empty();
            }
            log.info("튜토리얼 캐시 HIT: {} (naverBoard={})", stockCode, naverBoardScrapeEnabled);
            return Optional.of(envelope);
        } catch (Exception e) {
            log.warn("튜토리얼 캐시 읽기 실패 — 무시: {}", file, e);
            return Optional.empty();
        }
    }

    /**
     * 응답을 캐시에 기록합니다. 임시 파일에 쓴 뒤 rename 으로 교체합니다.
     */
    public void put(String stockCode, String corpName, String market, boolean naverBoardScrapeEnabled, AiTutorialResponse response) {
        if (!isValidCode(stockCode) || response == null) {
            return;
        }
        try {
            Path dir = ensureDirectory();
            long now = System.currentTimeMillis();
            long expires = now + ttlMinutes * 60_000L;
            TutorialCacheFile envelope = new TutorialCacheFile(
                    TutorialCacheFile.CURRENT_VERSION,
                    stockCode,
                    corpName != null ? corpName : "",
                    market != null ? market : "",
                    now,
                    expires,
                    response
            );
            byte[] json = objectMapper.writeValueAsBytes(envelope);
            Path target = filePath(stockCode, naverBoardScrapeEnabled);
            Path tmp = dir.resolve(stockCode + ".json." + Thread.currentThread().getId() + ".tmp");
            Files.write(tmp, json);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(tmp);
            }
            log.info("튜토리얼 캐시 저장 완료: {} (만료 ~{}분 후)", stockCode, ttlMinutes);
        } catch (Exception e) {
            log.warn("튜토리얼 캐시 저장 실패: {}", stockCode, e);
        }
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    private Path ensureDirectory() throws IOException {
        Path dir = Path.of(cacheDirectory).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * 네이버 종목토론 포함 여부에 따라 별도 파일을 씁니다(동일 종목이라도 토글에 따라 응답이 달라짐).
     */
    private Path filePath(String stockCode, boolean naverBoardScrapeEnabled) {
        try {
            String suffix = naverBoardScrapeEnabled ? ".json" : "-nb-off.json";
            return ensureDirectory().resolve(stockCode.trim() + suffix);
        } catch (IOException e) {
            throw new IllegalStateException("캐시 디렉터리 생성 실패", e);
        }
    }

    private static boolean isValidCode(String stockCode) {
        return stockCode != null && STOCK_CODE.matcher(stockCode.trim()).matches();
    }
}

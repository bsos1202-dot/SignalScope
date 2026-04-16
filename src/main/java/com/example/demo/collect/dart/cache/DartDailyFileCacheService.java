package com.example.demo.collect.dart.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.collect.dart.dto.DisclosureResponse;
import com.example.demo.collect.dart.dto.FinancialResponse;
import com.example.demo.collect.dart.dto.OwnershipResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * DART Open API JSON 응답을 <strong>한국시간(KST) 영업일 단위</strong>로 파일 캐시합니다.
 * 동일 일자·동일 키에 대해 하루 1회 수준으로 외부 호출을 줄입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DartDailyFileCacheService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper objectMapper;

    @Value("${app.dart-cache.directory:cache/dart/daily}")
    private String cacheRoot;

    @Value("${app.dart-cache.enabled:true}")
    private boolean enabled;

    public DisclosureResponse loadDisclosureList(
            String corpCode,
            String bgnDe,
            String endDe,
            String pageNo,
            String pageCount,
            Supplier<DisclosureResponse> onMiss
    ) {
        String key = safeCorp(corpCode) + "_list_" + bgnDe + "_" + endDe + "_p" + pageNo + "c" + pageCount + ".json";
        return loadJson(key, DisclosureResponse.class, onMiss);
    }

    public FinancialResponse loadFinancial(
            String corpCode,
            String bsnsYear,
            String reprtCode,
            Supplier<FinancialResponse> onMiss
    ) {
        String key = safeCorp(corpCode) + "_fn_" + bsnsYear + "_" + reprtCode + ".json";
        return loadJson(key, FinancialResponse.class, onMiss);
    }

    public OwnershipResponse loadOwnership(String corpCode, Supplier<OwnershipResponse> onMiss) {
        String key = safeCorp(corpCode) + "_ownership.json";
        return loadJson(key, OwnershipResponse.class, onMiss);
    }

    public DisclosureResponse loadMajorIssues(
            String corpCode,
            String bgnDe,
            String endDe,
            Supplier<DisclosureResponse> onMiss
    ) {
        String key = safeCorp(corpCode) + "_major_" + bgnDe + "_" + endDe + ".json";
        return loadJson(key, DisclosureResponse.class, onMiss);
    }

    public byte[] loadDisclosureDocument(String rceptNo, Supplier<byte[]> onMiss) {
        if (!enabled) {
            return onMiss.get();
        }
        String day = kstDayString();
        Path zipPath = dayDir(day).resolve("doc_" + safeReceipt(rceptNo) + ".zip");
        try {
            if (Files.isRegularFile(zipPath)) {
                byte[] bytes = Files.readAllBytes(zipPath);
                if (bytes.length > 0) {
                    log.info("DART 원문 ZIP 캐시 HIT: {}", rceptNo);
                    return bytes;
                }
            }
        } catch (Exception e) {
            log.warn("DART ZIP 캐시 읽기 실패 — 재조회: {}", rceptNo, e);
        }
        byte[] fresh = onMiss.get();
        if (fresh != null && fresh.length > 0) {
            try {
                Files.createDirectories(zipPath.getParent());
                Files.write(zipPath, fresh);
                log.info("DART 원문 ZIP 캐시 저장: {}", rceptNo);
            } catch (IOException e) {
                log.warn("DART ZIP 캐시 저장 실패: {}", rceptNo, e);
            }
        }
        return fresh;
    }

    private <T> T loadJson(String filename, Class<T> type, Supplier<T> onMiss) {
        if (!enabled) {
            return onMiss.get();
        }
        String day = kstDayString();
        Path path = dayDir(day).resolve(filename);
        try {
            if (Files.isRegularFile(path)) {
                T cached = objectMapper.readValue(path.toFile(), type);
                if (cached != null) {
                    log.info("DART JSON 캐시 HIT: {} / {}", day, filename);
                    return cached;
                }
            }
        } catch (Exception e) {
            log.warn("DART JSON 캐시 읽기 실패 — 재조회: {}", filename, e);
        }
        T fresh = onMiss.get();
        if (fresh != null) {
            try {
                Files.createDirectories(path.getParent());
                objectMapper.writeValue(path.toFile(), fresh);
                log.info("DART JSON 캐시 저장: {} / {}", day, filename);
            } catch (IOException e) {
                log.warn("DART JSON 캐시 저장 실패: {}", filename, e);
            }
        }
        return fresh;
    }

    private Path dayDir(String day) {
        Path root = Path.of(cacheRoot).toAbsolutePath().normalize();
        return root.resolve(day);
    }

    private static String kstDayString() {
        return LocalDate.now(KST).format(DAY);
    }

    private static String safeCorp(String corpCode) {
        if (corpCode == null || !corpCode.matches("^\\d{8}$")) {
            return "unknown";
        }
        return corpCode;
    }

    private static String safeReceipt(String rceptNo) {
        if (rceptNo == null || !rceptNo.matches("^\\d{14}$")) {
            return "invalid";
        }
        return rceptNo;
    }
}

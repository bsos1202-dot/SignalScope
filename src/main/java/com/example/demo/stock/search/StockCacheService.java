package com.example.demo.stock.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.stock.search.dto.StockInfo;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheService {

    private final ObjectMapper objectMapper;

    private static final String CACHE_FILE_PATH = "stock_list_cache.json";
    
    // 한국투자증권(KIS) 마스터 파일 실시간 다운로드 URL
    private static final String KOSPI_URL = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
    private static final String KOSDAQ_URL = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip";
    
    // 다량의 유입을 방어할 인메모리 저장소
    private List<StockInfo> memoryCache = new ArrayList<>();
    private String lastUpdatedDate = "";

    @PostConstruct
    public void init() {
        refreshCacheIfNeeded();
    }

    /**
     * 일자 전환 기준 (매일 새벽 6시) 갱신
     * (마스터 파일은 보통 영업일 새벽에 업데이트되므로 자정보다는 새벽 시간이 안전합니다.)
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void scheduledDailyUpdate() {
        log.info("일자 전환 기준 - 증권사 마스터 파일(KOSPI/KOSDAQ) 갱신을 시작합니다.");
        fetchFromMasterFileAndSave();
    }

    private synchronized void refreshCacheIfNeeded() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        File cacheFile = new File(CACHE_FILE_PATH);

        if (cacheFile.exists() && today.equals(lastUpdatedDate)) {
            log.info("오늘자 캐시 파일이 존재합니다. 메모리에 로드합니다.");
            loadFromFileToMemory(cacheFile);
        } else {
            log.info("캐시 갱신이 필요합니다. KIS 마스터 파일을 다운로드합니다.");
            fetchFromMasterFileAndSave();
        }
    }

    private void loadFromFileToMemory(File file) {
        try {
            memoryCache = objectMapper.readValue(file, new TypeReference<List<StockInfo>>() {});
            log.info("메모리 로드 완료: 총 {} 건", memoryCache.size());
        } catch (Exception e) {
            log.error("캐시 파일 읽기 실패", e);
        }
    }

    /**
     * KOSPI, KOSDAQ 마스터 파일을 모두 내려받아 파싱 후 파일/메모리에 저장합니다.
     */
    private void fetchFromMasterFileAndSave() {
        try {
            List<StockInfo> fetchedList = new ArrayList<>();
            
            log.info("KOSPI 마스터 파일 파싱 중...");
            // ⭐️ 시장 정보를 파라미터로 넘겨줍니다.
            fetchedList.addAll(downloadAndParseMaster(KOSPI_URL, "코스피"));
            
            log.info("KOSDAQ 마스터 파일 파싱 중...");
            fetchedList.addAll(downloadAndParseMaster(KOSDAQ_URL, "코스닥"));

            File file = new File(CACHE_FILE_PATH);
            objectMapper.writeValue(file, fetchedList);
            
            this.memoryCache = fetchedList;
            this.lastUpdatedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            
        } catch (Exception e) {
            log.error("마스터 파일 수집/파싱 실패", e);
        }
    }

    /**
     * URL에서 ZIP 파일을 스트림으로 바로 열고, 내부의 .mst 텍스트를 추출하는 헬퍼 메서드
     */
    private List<StockInfo> downloadAndParseMaster(String zipUrl, String marketName) throws Exception {
        List<StockInfo> list = new ArrayList<>();
        URL url = new URI(zipUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        // ZIP 파일을 디스크에 저장하지 않고 메모리 스트림으로 바로 해제합니다.
        try (ZipInputStream zis = new ZipInputStream(conn.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".mst")) {
                    
                    // KIS 마스터 파일은 CP949(EUC-KR의 확장)로 인코딩되어 있습니다.
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis, "MS949"));
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        if (line.length() <= 228) continue;
                        String part1 = line.substring(0, line.length() - 228);
                        if (part1.length() < 21) continue;

                        String shortCode = part1.substring(0, 9).trim();
                        String name = part1.substring(21).trim();

                        if (shortCode.startsWith("A") || shortCode.startsWith("J") || shortCode.startsWith("Q")) {
                            shortCode = shortCode.substring(1);
                        }

                        // ⭐️ 객체 생성 시 시장(marketName) 정보도 함께 담아줍니다.
                        list.add(new StockInfo(shortCode, name, marketName));
                    }
                }
            }
        }
        return list;
    }

    public List<StockInfo> getAllStocks() {
        return memoryCache;
    }
}
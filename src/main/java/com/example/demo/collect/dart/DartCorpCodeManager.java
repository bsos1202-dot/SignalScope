package com.example.demo.collect.dart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DartCorpCodeManager {

    private final RestTemplate restTemplate;

    @Value("${api.dart.key}") // DART API 키 (application.properties에 있는 키 이름 확인 필요)
    private String dartApiKey;

    // 6자리 주식코드 -> 8자리 DART 고유번호 매핑 저장소
    private final Map<String, String> stockToDartCodeMap = new ConcurrentHashMap<>();
    private final AtomicBoolean loadingStarted = new AtomicBoolean(false);
    private final AtomicBoolean loadingCompleted = new AtomicBoolean(false);
    private final AtomicInteger totalParsedEntries = new AtomicInteger(0);
    private final AtomicInteger mappedStockEntries = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        startAsyncLoadIfNeeded();
    }

    private void startAsyncLoadIfNeeded() {
        if (!loadingStarted.compareAndSet(false, true)) {
            return;
        }
        log.info("DART 고유번호 매핑 백그라운드 수집 시작");
        CompletableFuture.runAsync(this::loadDartCorpCodes);
    }

    private void loadDartCorpCodes() {
        boolean loadedSuccessfully = false;
        try {
            if (dartApiKey == null || dartApiKey.isBlank()) {
                log.error("DART API 키가 비어 있어 corpCode 로딩을 수행할 수 없습니다.");
                return;
            }
            String url = "https://opendart.fss.or.kr/api/corpCode.xml?crtfc_key=" + dartApiKey;
            byte[] zipData = restTemplate.getForObject(url, byte[].class);

            if (zipData == null) {
                log.warn("DART 고유번호 맵핑 파일 수집 결과가 비어 있습니다.");
                return;
            }

            // ZIP 압축 풀기
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry = zis.getNextEntry();
                if (entry != null && entry.getName().endsWith(".xml")) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        bos.write(buffer, 0, len);
                    }

                    // 추출한 XML 파싱
                    parseCorpCodeXml(bos.toByteArray());
                    loadedSuccessfully = true;
                }
            }
        } catch (Exception e) {
            log.error("DART 고유번호 맵핑 파일 수집 실패", e);
        } finally {
            if (loadedSuccessfully && loadingCompleted.get()) {
                log.info("DART 고유번호 로딩 최종 상태: {}", getLoadingStatusSummary());
            } else {
                log.warn("DART 고유번호 로딩이 완료되지 않았습니다. 현재 상태: {}", getLoadingStatusSummary());
                // 실패/중단 시 다음 요청에서 재시도할 수 있도록 시작 플래그를 되돌립니다.
                loadingStarted.set(false);
            }
        }
    }

    private void parseCorpCodeXml(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));

        NodeList list = doc.getElementsByTagName("list");
        totalParsedEntries.set(list.getLength());
        mappedStockEntries.set(0);
        log.info("DART XML 파싱 시작 - 전체 list 노드 {}건", list.getLength());
        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            String corpCode = element.getElementsByTagName("corp_code").item(0).getTextContent();
            String stockCode = element.getElementsByTagName("stock_code").item(0).getTextContent();

            // 상장사(stock_code가 있는 기업)만 맵에 저장합니다.
            if (stockCode != null && !stockCode.trim().isEmpty()) {
                stockToDartCodeMap.put(stockCode.trim(), corpCode.trim());
                mappedStockEntries.incrementAndGet();
            }

            if ((i + 1) % 5000 == 0) {
                log.info("DART XML 파싱 진행 - {}/{} 처리, 상장사 매핑 {}건",
                        i + 1, list.getLength(), mappedStockEntries.get());
            }
        }
        loadingCompleted.set(true);
        log.info("DART 매핑 데이터 로드 완료 (총 {}개 상장사)", stockToDartCodeMap.size());
    }

    /**
     * 6자리 주식코드를 입력하면 8자리 DART 코드를 반환합니다.
     */
    public String getDartCode(String stockCode) {
        if (stockToDartCodeMap.isEmpty()) {
            startAsyncLoadIfNeeded();
        }
        String result = stockToDartCodeMap.get(stockCode);
        if (result == null && !loadingCompleted.get()) {
            log.warn("DART 매핑 로딩 중 요청 유입 - 종목코드 {}, 상태: {}", stockCode, getLoadingStatusSummary());
        }
        return result;
    }

    public boolean isLoadingInProgress() {
        return loadingStarted.get() && !loadingCompleted.get();
    }

    public String getLoadingStatusSummary() {
        return String.format(
                "started=%s, completed=%s, mappedStocks=%d, parsedEntries=%d",
                loadingStarted.get(),
                loadingCompleted.get(),
                mappedStockEntries.get(),
                totalParsedEntries.get()
        );
    }
}
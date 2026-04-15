package com.example.demo.collect.dart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    @PostConstruct
    public void init() {
        log.info("DART 고유번호 매핑 데이터를 수집합니다.");
        loadDartCorpCodes();
    }

    private void loadDartCorpCodes() {
        try {
            String url = "https://opendart.fss.or.kr/api/corpCode.xml?crtfc_key=" + dartApiKey;
            byte[] zipData = restTemplate.getForObject(url, byte[].class);

            if (zipData == null) return;

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
                }
            }
        } catch (Exception e) {
            log.error("DART 고유번호 맵핑 파일 수집 실패", e);
        }
    }

    private void parseCorpCodeXml(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));

        NodeList list = doc.getElementsByTagName("list");
        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            String corpCode = element.getElementsByTagName("corp_code").item(0).getTextContent();
            String stockCode = element.getElementsByTagName("stock_code").item(0).getTextContent();

            // 상장사(stock_code가 있는 기업)만 맵에 저장합니다.
            if (stockCode != null && !stockCode.trim().isEmpty()) {
                stockToDartCodeMap.put(stockCode.trim(), corpCode.trim());
            }
        }
        log.info("DART 매핑 데이터 로드 완료 (총 {}개 상장사)", stockToDartCodeMap.size());
    }

    /**
     * 6자리 주식코드를 입력하면 8자리 DART 코드를 반환합니다.
     */
    public String getDartCode(String stockCode) {
        return stockToDartCodeMap.get(stockCode);
    }
}
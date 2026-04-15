package com.example.demo.collect.news;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleNewsApiClient {

    private final RestTemplate restTemplate;

    /**
     * 구글 뉴스 RSS를 호출하여 최신 뉴스 제목을 수집합니다.
     */
    public List<String> searchNews(String keyword, int displayCount) {
        List<String> newsList = new ArrayList<>();
        try {
            // 1. 구글 뉴스 RSS URL 구성 (한국어, 한국 지역 기준)
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = "https://news.google.com/rss/search?q=" + encodedKeyword + "&hl=ko&gl=KR&ceid=KR:ko";

            // 2. RSS XML 데이터 수집
            String xmlResponse = restTemplate.getForObject(new URI(url), String.class);
            if (xmlResponse == null) return newsList;

            // 3. 자바 기본 내장 XML 파서를 이용한 데이터 추출 (추가 의존성 불필요)
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlResponse.getBytes(StandardCharsets.UTF_8)));

            NodeList itemList = doc.getElementsByTagName("item");

            // 4. 요청한 개수만큼 뉴스 제목과 발행일 추출
            for (int i = 0; i < Math.min(itemList.getLength(), displayCount); i++) {
                Element item = (Element) itemList.item(i);
                String title = item.getElementsByTagName("title").item(0).getTextContent();
                String pubDate = item.getElementsByTagName("pubDate").item(0).getTextContent();
                
                newsList.add("- [구글] " + title + " (" + pubDate + ")");
            }

        } catch (Exception e) {
            log.error("구글 뉴스 수집 중 오류 발생", e);
        }
        return newsList;
    }
}
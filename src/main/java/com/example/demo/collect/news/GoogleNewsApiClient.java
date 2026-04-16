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
import java.util.Collections;
import java.util.List;

import com.example.demo.collect.news.dto.RssNewsItem;

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
        for (RssNewsItem item : searchNewsItems(keyword, displayCount)) {
            newsList.add("- [구글] " + item.title() + " (" + item.pubDate() + ")");
        }
        return newsList;
    }

    /**
     * 구글 뉴스 RSS 한 건씩(제목·링크·발행일) 반환합니다.
     */
    public List<RssNewsItem> searchNewsItems(String keyword, int displayCount) {
        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = "https://news.google.com/rss/search?q=" + encodedKeyword + "&hl=ko&gl=KR&ceid=KR:ko";

            String xmlResponse = restTemplate.getForObject(new URI(url), String.class);
            if (xmlResponse == null) {
                return List.of();
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlResponse.getBytes(StandardCharsets.UTF_8)));

            NodeList itemList = doc.getElementsByTagName("item");
            List<RssNewsItem> out = new ArrayList<>();
            for (int i = 0; i < Math.min(itemList.getLength(), displayCount); i++) {
                Element item = (Element) itemList.item(i);
                String title = text(item, "title");
                String link = text(item, "link");
                String pubDate = text(item, "pubDate");
                out.add(new RssNewsItem(title != null ? title : "", link != null ? link : "", pubDate != null ? pubDate : ""));
            }
            return out;
        } catch (Exception e) {
            log.error("구글 뉴스 수집 중 오류 발생", e);
            return Collections.emptyList();
        }
    }

    private static String text(Element item, String tag) {
        NodeList nodes = item.getElementsByTagName(tag);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }
}
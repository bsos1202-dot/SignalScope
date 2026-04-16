package com.example.demo.collect.naverboard;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.example.demo.collect.naverboard.dto.StockBoardPost;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 네이버 금융 종목토론방({@code /item/board.naver}) 목록 HTML을 파싱합니다.
 * <p>
 * 공식 API가 아니므로 페이지 구조 변경 시 수정이 필요할 수 있습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NaverStockBoardClient {

    private static final String FINANCE_BASE = "https://finance.naver.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final RestTemplate restTemplate;

    /**
     * 종목토론 게시판에서 최신 글을 {@code limit}개까지 가져옵니다 (6자리 단축코드).
     */
    public List<StockBoardPost> fetchLatestPosts(String stockCode6, int limit) {
        if (stockCode6 == null || stockCode6.isBlank()) {
            return List.of();
        }
        String code = stockCode6.trim();
        String url = FINANCE_BASE + "/item/board.naver?code=" + code;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.ALL));
            headers.add(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.add(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en;q=0.8");
            String html = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            ).getBody();
            if (html == null || html.isBlank()) {
                return List.of();
            }
            Document doc = Jsoup.parse(html, FINANCE_BASE);
            Elements rows = doc.select("table.type2 tbody tr[onmouseover]");
            List<StockBoardPost> out = new ArrayList<>();
            for (Element row : rows) {
                Elements tds = row.select("> td");
                if (tds.size() < 6) {
                    continue;
                }
                Element link = tds.get(1).selectFirst("a[href*='board_read.naver']");
                if (link == null) {
                    continue;
                }
                String title = link.text().trim();
                if (title.isEmpty()) {
                    title = link.attr("title").trim();
                }
                String href = link.attr("abs:href");
                if (href.isEmpty()) {
                    href = FINANCE_BASE + link.attr("href");
                }
                String postedAt = tds.get(0).text().trim();
                String author = tds.get(2).text().trim().replace('\u00a0', ' ');
                int views = parseInt(tds.get(3).text());
                int up = parseInt(tds.get(4).select("strong").text());
                int down = parseInt(tds.get(5).select("strong").text());
                out.add(new StockBoardPost(postedAt, title, href, author, views, up, down));
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("네이버 종목토론방 스크래핑 실패: code={}, url={}", code, url, e);
            return List.of();
        }
    }

    private static int parseInt(String raw) {
        if (raw == null) {
            return 0;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

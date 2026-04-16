package com.example.demo.collect.hanwha;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.demo.collect.hanwha.dto.HanwhaResearchListItem;

import lombok.extern.slf4j.Slf4j;

/**
 * 한화투자증권 WM 투자정보 &gt; 기업·산업분석 목록(목록형 1페이지) HTML 스크래핑.
 * <p>비공식·구조 변경에 취약하므로 {@link com.example.demo.collect.hanwha.cache.HanwhaResearchDailyCacheService}와 함께 사용합니다.
 */
@Slf4j
@Component
public class HanwhaResearchListClient {

    private static final Pattern VIEW_JS = Pattern.compile(
            "view\\(\\s*'(?<seq>\\d+)'\\s*,\\s*'(?<depth3>[^']+)'",
            Pattern.CASE_INSENSITIVE
    );

    private static final String DEFAULT_BASE = "https://www.hanwhawm.com";

    @Value("${app.hanwha-research.list-url:https://www.hanwhawm.com/main/research/main/list.cmd?depth2_id=1002&mode=depth2}")
    private String listUrl;

    @Value("${app.hanwha-research.site-base:https://www.hanwhawm.com}")
    private String siteBase;

    @Value("${app.hanwha-research.connect-timeout-ms:20000}")
    private int connectTimeoutMs;

    /**
     * 목록 페이지의 {@code ul.researchList} 첫 페이지 항목만 수집합니다.
     */
    public List<HanwhaResearchListItem> fetchFirstPage() {
        List<HanwhaResearchListItem> out = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(listUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(connectTimeoutMs)
                    .followRedirects(true)
                    .get();
            Elements lis = doc.select("ul.researchList > li");
            String base = normalizeBase(siteBase);
            for (Element li : lis) {
                Element tit = li.selectFirst("div.tit");
                if (tit == null) {
                    continue;
                }
                Element a = tit.selectFirst("a[href]");
                if (a == null) {
                    continue;
                }
                String href = a.attr("href");
                if (href == null || href.isBlank()) {
                    continue;
                }
                Matcher m = VIEW_JS.matcher(href);
                if (!m.find()) {
                    continue;
                }
                String seq = m.group("seq");
                String depth3 = m.group("depth3");
                String title = a.text().trim();
                if (title.isEmpty()) {
                    continue;
                }
                Element cont = tit.selectFirst("p.cont_txt");
                String snippet = cont != null ? cont.text().trim() : "";
                Element info = li.selectFirst("p.info");
                String category = "";
                String author = "";
                String publishedAt = "";
                if (info != null) {
                    Element gubun = info.selectFirst("span.gubun");
                    Element date = info.selectFirst("span.date");
                    category = gubun != null ? gubun.text().trim() : "";
                    publishedAt = date != null ? date.text().trim() : "";
                    author = info.text()
                            .replace(category, "")
                            .replace(publishedAt, "")
                            .trim();
                }
                String link = base + "/main/research/main/view.cmd?depth3_id=" + depth3 + "&seq=" + seq + "&p=";
                out.add(new HanwhaResearchListItem(title, link, category, author, publishedAt, snippet));
            }
            log.info("한화 WM 리서치 목록 파싱: {}건", out.size());
        } catch (Exception e) {
            log.warn("한화 WM 리서치 목록 스크래핑 실패", e);
        }
        return out;
    }

    private static String normalizeBase(String base) {
        if (base == null || base.isBlank()) {
            return DEFAULT_BASE;
        }
        String b = base.trim();
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }
}

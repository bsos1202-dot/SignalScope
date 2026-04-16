package com.example.demo.ai.openai;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.example.demo.collect.dart.DartApiClient;
import com.example.demo.collect.dart.cache.DartDailyFileCacheService;
import com.example.demo.collect.dart.dto.DisclosureResponse;
import com.example.demo.collect.dart.dto.FinancialResponse;
import com.example.demo.collect.dart.dto.OwnershipResponse;
import com.example.demo.collect.kis.KisStockApiClient;
import com.example.demo.collect.kis.KisTokenManager;
import com.example.demo.collect.kis.dto.KisCcnlResponse;
import com.example.demo.collect.kis.dto.KisInvestmentIndicatorResponse;
import com.example.demo.collect.kis.dto.KisStockResponse;
import com.example.demo.collect.kis.dto.KisStockResponse.Output;
import com.example.demo.collect.naverboard.NaverStockBoardClient;
import com.example.demo.collect.naverboard.dto.StockBoardPost;
import com.example.demo.ai.dto.AiTutorialResponse;
import com.example.demo.ai.dto.KisMarketMetrics;
import com.example.demo.ai.dto.TutorialEvidence;
import com.example.demo.ai.dto.TutorialEvidence.BoardRef;
import com.example.demo.ai.dto.TutorialEvidence.DisclosureRef;
import com.example.demo.ai.dto.TutorialEvidence.NewsRef;
import com.example.demo.collect.news.GoogleNewsApiClient;
import com.example.demo.collect.news.NaverNewsApiClient;
import com.example.demo.collect.news.cache.MarketNewsFileCacheService;
import com.example.demo.collect.news.dto.NaverNewsResponse;
import com.example.demo.collect.news.dto.RssNewsItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockAiAnalysisService {

    private final DartApiClient dartApiClient;
    private final DartDailyFileCacheService dartDailyFileCacheService;
    private final MarketNewsFileCacheService marketNewsFileCacheService;
    private final NaverNewsApiClient naverNewsApiClient;
    private final GoogleNewsApiClient googleNewsApiClient;
    private final KisStockApiClient kisStockApiClient;
    private final KisTokenManager kisTokenManager;
    private final OpenAiDirectClient openAiDirectClient;
    private final NaverStockBoardClient naverStockBoardClient;

    public AiTutorialResponse generateWhyApproachTutorial(String stockCode, String dartCode, String corpName, String market, String today) {
        log.info("{} 종목에 대한 실시간 AI 튜토리얼 분석 시작", corpName);

        // 1. 시장 현재 상태 수집 (한국투자증권 — 거래량·밸류·RSI·체결강도 등; LLM 입력에서는 등락·가격·52주% 제외)
        String validAccessToken = kisTokenManager.getAccessToken();
        KisStockResponse stockData = kisStockApiClient.getCurrentPrice(stockCode, validAccessToken);
        KisInvestmentIndicatorResponse kisInvest = kisStockApiClient.getInvestmentIndicator(stockCode, validAccessToken);
        KisCcnlResponse kisCcnl = kisStockApiClient.getTimeConclusion(stockCode, validAccessToken);
        KisMarketMetrics kisMetrics = buildKisMarketMetrics(stockData, kisInvest, kisCcnl);
        String parsedStockStatus = kisMetrics.llmContextBlock();

        // 2. 팩트 데이터 수집 (DART — KST 일자별 파일 캐시로 동일 일 재호출 최소화)
        DisclosureResponse disclosureData = dartDailyFileCacheService.loadDisclosureList(
                dartCode, today, today, "1", "10",
                () -> dartApiClient.searchDisclosures(dartCode, today, today, "1", "10"));
        FinancialResponse financialData = dartDailyFileCacheService.loadFinancial(
                dartCode, "2023", "11011",
                () -> dartApiClient.getFinancialInfo(dartCode, "2023", "11011"));
        OwnershipResponse ownershipData = dartDailyFileCacheService.loadOwnership(
                dartCode, () -> dartApiClient.getOwnershipDisclosures(dartCode));
        DisclosureResponse majorIssuesData = dartDailyFileCacheService.loadMajorIssues(
                dartCode, today, today,
                () -> dartApiClient.getMajorManagementIssues(dartCode, today, today));

        // 공시 상세 원문(ZIP) 수집 및 텍스트 파싱
        String documentSummary = "최신 원문 없음";
        if (disclosureData != null && disclosureData.list() != null && !disclosureData.list().isEmpty()) {
            String rceptNo = disclosureData.list().get(0).rceptNo();
            byte[] zipData = dartDailyFileCacheService.loadDisclosureDocument(
                    rceptNo, () -> dartApiClient.getDisclosureDocument(rceptNo));
            documentSummary = extractTextFromZip(zipData);
        }

        String parsedDisclosures = extractDisclosures(disclosureData);
        String parsedFinancials = extractFinancials(financialData);
        String ownershipLlmBlock = buildOwnershipLlmBlock(ownershipData);
        String parsedMajorIssues = extractDisclosures(majorIssuesData); // 재사용

        // 3. 시장 반응 데이터 수집 (멀티 채널 뉴스 - 종목명 기반 검색)
        String newsKeyword = corpName + " 주식";
        
        NaverNewsResponse naverData = naverNewsApiClient.searchNews(newsKeyword, 3);
        String parsedNaverNews = extractNaverNews(naverData);
        
        List<RssNewsItem> googleRssKeyword = googleNewsApiClient.searchNewsItems(newsKeyword, 5);
        List<String> googleNewsList = googleRssKeyword.stream()
                .map(i -> "- [구글] " + i.title() + " (" + i.pubDate() + ")")
                .collect(Collectors.toList());
        String parsedGoogleNews = String.join("\n", googleNewsList);

        String marketNewsKeyword = (market != null && !market.isBlank()) ? (market.trim() + " 증시") : "";
        NaverNewsResponse naverMarketData = null;
        List<RssNewsItem> googleRssMarket = Collections.emptyList();
        if (!marketNewsKeyword.isBlank()) {
            var marketCached = marketNewsFileCacheService.readIfValid(market.trim());
            if (marketCached.isPresent()) {
                naverMarketData = marketCached.get().naverMarket();
                googleRssMarket = marketCached.get().googleMarket();
            } else {
                naverMarketData = naverNewsApiClient.searchNews(marketNewsKeyword, 3);
                googleRssMarket = googleNewsApiClient.searchNewsItems(market.trim(), 3);
                marketNewsFileCacheService.put(market.trim(), naverMarketData, googleRssMarket);
            }
        }
        String parsedNaverNewsMarket = marketNewsKeyword.isBlank()
                ? "(시장 구분 없음 — 시장 키워드 뉴스 생략)"
                : extractNaverNews(naverMarketData);
        List<String> googleNewsListMarket = googleRssMarket.stream()
                .map(i -> "- [구글] " + i.title() + " (" + i.pubDate() + ")")
                .collect(Collectors.toList());
        String parsedGoogleNewsMarket = marketNewsKeyword.isBlank()
                ? "(시장 구분 없음 — 시장 키워드 뉴스 생략)"
                : String.join("\n", googleNewsListMarket);

        // 4. 네이버 종목토론방 최신 글 (비공식 HTML 스크래핑)
        List<StockBoardPost> boardPosts = naverStockBoardClient.fetchLatestPosts(stockCode, 5);
        String parsedBoardPosts = formatNaverBoardPosts(boardPosts);
        String boardSentimentHint = summarizeBoardSentiment(boardPosts);
        
        log.info("[한국투자증권] 주가정보 - " + parsedStockStatus);
        log.info("[DART] 공시정보 - " + parsedFinancials);
        log.info("[DART] 소유정보 - {}", ownershipLlmBlock.isBlank()
                ? "(발췌 없음 — 분석·증거에서 제외)"
                : ownershipLlmBlock.replace("\n", " | "));
        log.info("[DART] 주요정보 - " + parsedMajorIssues);
        log.info("[네이버API] 뉴스 - " + parsedNaverNews);
        log.info("[구글RSS] 뉴스 - " + parsedGoogleNews);
        log.info("[네이버API] 시장 키워드 뉴스 - {}", parsedNaverNewsMarket);
        log.info("[구글RSS] 시장 키워드 뉴스 - {}", parsedGoogleNewsMarket);
        log.info("[네이버종목토론] 요약 - {}", boardSentimentHint);

        // 5. WHY Approach 시스템 프롬프트 조립
        String systemPrompt = """
            당신은 주식 투자를 처음 시작하는 사람들에게 단기간 종목이 왜 움직였는지를 알려주는 가이드입니다.
            많은 사람들이 주식 가격이 왜 움직였는지 이해를 돕는 것이 우리의 가장 큰 목표입니다.
            사용자들은 지식의 편차가 심하므로, 전문 용어를 최대한 배제하고 쉽게 비유해서 설명해야 합니다.
            입력 데이터에는 등락률·현재가·52주 대비 등이 포함되어 있지 않습니다. **전일 대비 몇 % 올랐다/내렸다, 현재가, 52주 대비 몇 %** 같은 표현은 하지 마세요. 거래량·거래대금·회전율·체결강도·PER·RSI·공시·뉴스·토론 분위기 위주로 맥락을 설명하세요.
            제공된 '시장 참고 수치'를 먼저 짚어주고, 공시와 뉴스를 바탕으로 분위기와 배경을 인과관계로 설명하세요.
            PER·PBR·RSI·체결강도 등은 증권사 API가 준 참고 수치일 뿐이며, 투자의 정답이나 매수·매도 신호가 아님을 유의해 설명하세요.
            네이버 종목토론방 글은 개인 의견·감정에 가깝고 추천/비추천 수도 참고용입니다. 여론을 사실처럼 단정하지 말고, '커뮤니티에서는 이런 분위기가 보인다' 수준으로 짧게 언급하세요.
            사용자가 스스로 시장을 이해하고 당사 MTS에 계속 머물며 학습하고 싶도록 작성해 주세요.
            """;

        String userPrompt = String.format("""
            다음은 특정 기업의 실시간 데이터입니다.
            
            [현재 시장 상황 — 한국투자증권 시세·지표]
            1. 실시간 시세 및 참고 지표(PER, RSI, 체결강도 등): %s
            
            [핵심 팩트 데이터]
            2. 발생한 공시: %s
            3. 재무 지표: %s
            4. 공시 상세 원문 요약: %s
            %s
            [시장 반응 데이터]
            5. 네이버 뉴스 동향(종목 키워드):
            %s
            6. 구글 뉴스 동향(종목 키워드):
            %s
            7. 네이버 뉴스(시장 키워드 — 코스피/코스닥 등):
            %s
            8. 구글 RSS 뉴스(시장 키워드):
            %s
            
            [커뮤니티 반응 — 네이버 종목토론방 최신 글 5개]
            9. 여론 요약(추천/비추천 합계 기반 참고): %s
            %s
            
            위 사실을 바탕으로:
            - 거래 활발도(거래량·대금·회전·체결강도)와 밸류에이션 맥락은 어떤가?
            - 공시나 뉴스가 시장 분위기에 어떤 배경을 주고 있는가?
            - 네이버 종목토론방에서는 긍정/부정적 반응이 어떻게 보이는지 한 문장으로?
            를 3~4줄로 쉽게 설명해 줘. (등락률·현재가·%% 수치는 언급하지 말 것.)
            """, parsedStockStatus, parsedDisclosures, parsedFinancials, documentSummary,
                ownershipSectionForPrompt(ownershipLlmBlock),
                parsedNaverNews, parsedGoogleNews, parsedNaverNewsMarket, parsedGoogleNewsMarket,
                boardSentimentHint, parsedBoardPosts);

        String summary = openAiDirectClient.requestChatCompletion(systemPrompt, userPrompt);
        TutorialEvidence evidence = buildTutorialEvidence(
                kisMetrics.headlineQuote(),
                kisMetrics,
                disclosureData,
                majorIssuesData,
                financialData,
                ownershipData,
                documentSummary,
                naverData,
                googleRssKeyword,
                naverMarketData,
                googleRssMarket,
                boardPosts,
                boardSentimentHint
        );
        return new AiTutorialResponse(summary, evidence);
    }

    private TutorialEvidence buildTutorialEvidence(
            String stockQuoteLine,
            KisMarketMetrics kisMarketMetrics,
            DisclosureResponse disclosureData,
            DisclosureResponse majorIssuesData,
            FinancialResponse financialData,
            OwnershipResponse ownershipData,
            String disclosureDocumentSnippet,
            NaverNewsResponse naverData,
            List<RssNewsItem> googleRssKeyword,
            NaverNewsResponse naverMarketData,
            List<RssNewsItem> googleRssMarket,
            List<StockBoardPost> boardPosts,
            String boardSentimentHint
    ) {
        return new TutorialEvidence(
                stockQuoteLine,
                kisMarketMetrics,
                toDisclosureRefs(disclosureData),
                toDisclosureRefs(majorIssuesData),
                toFinancialLines(financialData),
                toOwnershipLines(ownershipData),
                disclosureDocumentSnippet,
                toNaverNewsRefs(naverData),
                toGoogleNewsRefs(googleRssKeyword),
                toNaverNewsRefs(naverMarketData),
                toGoogleNewsRefs(googleRssMarket),
                toBoardRefs(boardPosts),
                boardSentimentHint
        );
    }

    private KisMarketMetrics buildKisMarketMetrics(
            KisStockResponse price,
            KisInvestmentIndicatorResponse invest,
            KisCcnlResponse ccnl
    ) {
        String headline = extractStockStatusHeadline(price);
        if (price == null || price.output() == null) {
            return new KisMarketMetrics(headline, "", "", "", "", "", "", "", "", "", "", "", headline);
        }
        Output o = price.output();
        String rsi = "";
        if (invest != null && invest.output() != null && invest.output().rsi() != null) {
            rsi = invest.output().rsi().trim();
        }
        String strength = "";
        String stTime = "";
        if (ccnl != null && ccnl.output() != null && !ccnl.output().isEmpty()) {
            var row = ccnl.output().get(0);
            if (row.contractStrength() != null) {
                strength = row.contractStrength().trim();
            }
            if (row.contractHour() != null) {
                stTime = row.contractHour().trim();
            }
        }
        String block = buildKisLlmContext(o, rsi, strength, stTime);
        return new KisMarketMetrics(
                headline,
                nz(o.per()),
                nz(o.pbr()),
                nz(o.eps()),
                nz(o.bps()),
                rsi,
                nz(o.volumeTurnoverRate()),
                "",
                "",
                nz(o.foreignHoldingRatio()),
                strength,
                stTime,
                block
        );
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static void appendKisMetric(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append("\n- ").append(label).append(": ").append(value.trim());
    }

    /**
     * LLM 입력용: 등락률·현재가·52주 대비(%) 등 실시간 변동 큰 수치는 제외하고, 거래량·회전·밸류·RSI·체결강도 중심.
     */
    private String buildKisLlmContext(Output o, String rsi, String strength, String stTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("한국투자증권 기준 요약(등락률·현재가·52주 대비율은 변동·지연이 커 본 분석에는 포함하지 않음):");
        appendKisMetric(sb, "당일 누적 거래량(주)", o.volume());
        appendKisMetric(sb, "누적 거래대금(원)", o.accumulatedTradeAmount());
        appendKisMetric(sb, "전일 대비 거래량 비율(%)", o.volumeVsPrevDayRate());
        appendKisMetric(sb, "거래량 회전율(%)", o.volumeTurnoverRate());
        appendKisMetric(sb, "PER", o.per());
        appendKisMetric(sb, "PBR", o.pbr());
        appendKisMetric(sb, "EPS", o.eps());
        appendKisMetric(sb, "BPS", o.bps());
        appendKisMetric(sb, "외국인 소진율(%)", o.foreignHoldingRatio());
        if (!rsi.isBlank()) {
            sb.append("\n- RSI(상대강도지수): ").append(rsi);
        }
        if (!strength.isBlank()) {
            sb.append("\n- 당일 체결강도: ").append(strength);
            if (!stTime.isBlank()) {
                sb.append(" (참고 체결시각 ").append(stTime).append(")");
            }
        }
        return sb.toString();
    }

    private List<DisclosureRef> toDisclosureRefs(DisclosureResponse data) {
        if (data == null || data.list() == null || data.list().isEmpty()) {
            return List.of();
        }
        return data.list().stream()
                .map(item -> new DisclosureRef(
                        item.rceptDt() != null ? item.rceptDt() : "",
                        item.reportNm() != null ? item.reportNm() : "",
                        item.rceptNo() != null ? item.rceptNo() : ""
                ))
                .collect(Collectors.toList());
    }

    private List<String> toFinancialLines(FinancialResponse data) {
        if (data == null || data.list() == null || data.list().isEmpty()) {
            return List.of();
        }
        return data.list().stream()
                .limit(8)
                .map(item -> item.accountNm() + ": " + formatNumber(item.currentAmount()) + "원")
                .collect(Collectors.toList());
    }

    private List<String> toOwnershipLines(OwnershipResponse data) {
        if (!hasMeaningfulOwnership(data)) {
            return List.of();
        }
        return data.list().stream()
                .filter(this::ownershipItemHasContent)
                .limit(5)
                .map(this::formatOwnershipEvidenceLine)
                .collect(Collectors.toList());
    }

    /** 보고자·소유수·지분율 중 하나라도 유효하면 true (문자열 "null"·공백은 무효). */
    private boolean hasMeaningfulOwnership(OwnershipResponse data) {
        if (data == null || data.list() == null || data.list().isEmpty()) {
            return false;
        }
        return data.list().stream().anyMatch(this::ownershipItemHasContent);
    }

    private boolean ownershipItemHasContent(OwnershipResponse.OwnershipItem item) {
        if (item == null) {
            return false;
        }
        return !isBlankOrNullLiteral(item.repror())
                || !isBlankOrNullLiteral(item.ownedQty())
                || !isBlankOrNullLiteral(item.ownershipRate());
    }

    private static boolean isBlankOrNullLiteral(String s) {
        if (s == null) {
            return true;
        }
        String t = s.trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t);
    }

    private String formatOwnershipEvidenceLine(OwnershipResponse.OwnershipItem item) {
        List<String> parts = new ArrayList<>();
        if (!isBlankOrNullLiteral(item.repror())) {
            parts.add("보고자: " + item.repror().trim());
        }
        if (!isBlankOrNullLiteral(item.ownedQty())) {
            parts.add("소유주식수: " + item.ownedQty().trim());
        }
        if (!isBlankOrNullLiteral(item.ownershipRate())) {
            parts.add("지분율: " + item.ownershipRate().trim() + "%");
        }
        return String.join(", ", parts);
    }

    /** LLM용 지분 발췌 본문(항목당 한 줄). 유효 데이터 없으면 빈 문자열. */
    private String buildOwnershipLlmBlock(OwnershipResponse data) {
        if (!hasMeaningfulOwnership(data)) {
            return "";
        }
        return data.list().stream()
                .filter(this::ownershipItemHasContent)
                .limit(5)
                .map(item -> "- " + formatOwnershipEvidenceLine(item))
                .collect(Collectors.joining("\n"));
    }

    private static String ownershipSectionForPrompt(String ownershipLlmBlock) {
        if (ownershipLlmBlock == null || ownershipLlmBlock.isBlank()) {
            return "";
        }
        return """
            
            [지분 공시 발췌 — DART 임원·주요주주 소유보고]
            """ + ownershipLlmBlock;
    }

    private List<NewsRef> toNaverNewsRefs(NaverNewsResponse data) {
        if (data == null || data.items() == null || data.items().isEmpty()) {
            return List.of();
        }
        List<NewsRef> out = new ArrayList<>();
        for (var item : data.items()) {
            try {
                String cleanTitle = item.title() != null ? item.title().replaceAll("<[^>]+>", "") : "";
                String cleanDesc = item.description() != null ? item.description().replaceAll("<[^>]+>", "") : "";
                cleanTitle = HtmlUtils.htmlUnescape(cleanTitle);
                cleanDesc = HtmlUtils.htmlUnescape(cleanDesc);
                String link = item.originallink() != null && !item.originallink().isBlank()
                        ? item.originallink()
                        : (item.link() != null ? item.link() : "");
                out.add(new NewsRef(
                        cleanTitle,
                        link,
                        item.pubDate() != null ? item.pubDate() : "",
                        cleanDesc
                ));
            } catch (Exception e) {
                log.debug("네이버 뉴스 항목 매핑 생략", e);
            }
        }
        return out;
    }

    private List<NewsRef> toGoogleNewsRefs(List<RssNewsItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(i -> new NewsRef(i.title(), i.link(), i.pubDate(), ""))
                .collect(Collectors.toList());
    }

    private List<BoardRef> toBoardRefs(List<StockBoardPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .map(p -> new BoardRef(
                        p.postedAt(),
                        p.title(),
                        p.url(),
                        p.upvotes(),
                        p.downvotes(),
                        p.author()
                ))
                .collect(Collectors.toList());
    }

    // --- 데이터 추출 헬퍼 메서드들 ---

    /**
     * UI·근거 카드용: 등락률·현재가 없이 거래량·대금 중심 (캐시·지연 시 혼동 방지).
     */
    private String extractStockStatusHeadline(KisStockResponse data) {
        if (data == null || data.output() == null) {
            return "- 현재 시세 정보를 불러올 수 없습니다.";
        }
        String volume = formatNumber(data.output().volume());
        String pbmn = formatNumber(data.output().accumulatedTradeAmount());
        StringBuilder sb = new StringBuilder();
        sb.append("당일 누적 거래량 약 ").append(volume).append("주");
        if (pbmn != null && !pbmn.isBlank() && !"0".equals(pbmn.replace(",", ""))) {
            sb.append(", 누적 거래대금 약 ").append(pbmn).append("원");
        }
        sb.append(". (등락률·현재가는 실시간 변동이 커 요약·AI 입력에서는 제외)");
        return sb.toString();
    }

    private String extractDisclosures(DisclosureResponse data) {
        if (data == null || data.list() == null || data.list().isEmpty()) return "- 특이사항 없음";
        return data.list().stream()
                .map(item -> "- [" + item.rceptDt() + "] " + item.reportNm())
                .collect(Collectors.joining("\n"));
    }

    private String extractFinancials(FinancialResponse data) {
        if (data == null || data.list() == null || data.list().isEmpty()) return "- 재무 정보 업데이트 없음";
        return data.list().stream()
                .map(item -> "- " + item.accountNm() + ": " + formatNumber(item.currentAmount()) + "원")
                .collect(Collectors.joining("\n"));
    }

    private String extractTextFromZip(byte[] zipData) {
        if (zipData == null || zipData.length == 0) return "원문 데이터 없음";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry != null) {
                byte[] buffer = new byte[1024];
                int len = zis.read(buffer);
                if (len > 0) {
                    String xmlContent = new String(buffer, 0, len, StandardCharsets.UTF_8);
                    String plainText = xmlContent.replaceAll("<[^>]+>", " ").trim();
                    return plainText.length() > 300 ? plainText.substring(0, 300) + "..." : plainText;
                }
            }
        } catch (Exception e) {
            log.error("ZIP 원문 풀기 실패", e);
        }
        return "원문 파싱 실패";
    }

    private String formatNaverBoardPosts(List<StockBoardPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return "- 종목토론방 목록을 가져오지 못했거나 게시글이 없습니다.";
        }
        return posts.stream()
                .map(p -> String.format(
                        "- [%s] %s (추천 %d / 비추천 %d, 조회 %d, 작성자 %s)",
                        p.postedAt(),
                        p.title(),
                        p.upvotes(),
                        p.downvotes(),
                        p.views(),
                        p.author()
                ))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 추천·비추천 합계로 대략적인 톤만 잡아 LLM에 힌트로 넘깁니다 (정밀 감성분석 아님).
     */
    private String summarizeBoardSentiment(List<StockBoardPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return "토론방 데이터 없음";
        }
        int up = posts.stream().mapToInt(StockBoardPost::upvotes).sum();
        int down = posts.stream().mapToInt(StockBoardPost::downvotes).sum();
        String tone;
        if (up + down == 0) {
            tone = "최근 글에서 추천·비추천이 거의 없어 여론 방향을 숫자로 보기 어렵습니다.";
        } else if (up > down * 2) {
            tone = "최근 글 기준으로 추천이 비추천보다 많아 다소 긍정적인 반응으로 보입니다.";
        } else if (down > up * 2) {
            tone = "비추천이 상대적으로 많아 다소 부정적인 반응이 섞여 있습니다.";
        } else {
            tone = "긍정과 부정 반응이 비슷하거나 혼재합니다.";
        }
        return String.format("%s (최근 %d개 글 기준 추천 합 %d, 비추천 합 %d)", tone, posts.size(), up, down);
    }

    private String extractNaverNews(NaverNewsResponse data) {
        if (data == null || data.items() == null || data.items().isEmpty()) {
            return "- 관련 최신 뉴스가 없습니다.";
        }
        return data.items().stream()
                .map(item -> {
                    try {
                        String cleanTitle = item.title().replaceAll("<[^>]+>", "");
                        String cleanDesc = item.description().replaceAll("<[^>]+>", "");
                        cleanTitle = HtmlUtils.htmlUnescape(cleanTitle);
                        cleanDesc = HtmlUtils.htmlUnescape(cleanDesc);
                        return "- 제목: " + cleanTitle + " (요약: " + cleanDesc + ")";
                    } catch (Exception e) {
                        return "- 뉴스 파싱 오류";
                    }
                })
                .collect(Collectors.joining("\n"));
    }

    private String formatNumber(String numberStr) {
        if (numberStr == null || numberStr.isEmpty()) return "0";
        try {
            // "1,234" 형태의 문자열이 들어올 경우를 대비해 쉼표를 먼저 제거합니다.
            long number = Long.parseLong(numberStr.replaceAll(",", ""));
            return String.format("%,d", number); 
        } catch (NumberFormatException e) {
            return numberStr;
        }
    }
}
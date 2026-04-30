package com.example.demo.ai.openai;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.example.demo.collect.dart.DartApiClient;
import com.example.demo.collect.hanwha.HanwhaResearchListClient;
import com.example.demo.collect.hanwha.cache.HanwhaResearchDailyCacheService;
import com.example.demo.collect.hanwha.dto.HanwhaResearchListItem;
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
import com.example.demo.ai.dto.TutorialEvidence.HanwhaResearchRef;
import com.example.demo.ai.dto.TutorialEvidence.NewsRef;
import com.example.demo.collect.news.GoogleNewsApiClient;
import com.example.demo.collect.news.NaverNewsApiClient;
import com.example.demo.collect.news.cache.MarketNewsFileCacheService;
import com.example.demo.collect.news.dto.NaverNewsResponse;
import com.example.demo.collect.news.dto.RssNewsItem;
import com.example.demo.config.TutorialFetchAsyncConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class StockAiAnalysisService {

    @Value("${app.hanwha-research.enabled:true}")
    private boolean hanwhaResearchEnabled;

    @Value("${app.news.naver.stock-display-count:3}")
    private int naverNewsStockDisplayCount;

    @Value("${app.news.google.stock-display-count:5}")
    private int googleNewsStockDisplayCount;

    @Value("${app.news.naver.market-display-count:3}")
    private int naverNewsMarketDisplayCount;

    @Value("${app.news.google.market-display-count:3}")
    private int googleNewsMarketDisplayCount;

    @Value("${app.tutorial-ui.market-data-provider-label:한국투자증권}")
    private String tutorialMarketDataProviderLabel;

    @Value("${app.tutorial-ui.research-provider-label:한화 WM}")
    private String tutorialResearchProviderLabel;

    private final DartApiClient dartApiClient;
    private final DartDailyFileCacheService dartDailyFileCacheService;
    private final MarketNewsFileCacheService marketNewsFileCacheService;
    private final NaverNewsApiClient naverNewsApiClient;
    private final GoogleNewsApiClient googleNewsApiClient;
    private final KisStockApiClient kisStockApiClient;
    private final KisTokenManager kisTokenManager;
    private final OpenAiDirectClient openAiDirectClient;
    private final NaverStockBoardClient naverStockBoardClient;
    private final HanwhaResearchListClient hanwhaResearchListClient;
    private final HanwhaResearchDailyCacheService hanwhaResearchDailyCacheService;

    private final Executor tutorialFetchExecutor;

    public StockAiAnalysisService(
            DartApiClient dartApiClient,
            DartDailyFileCacheService dartDailyFileCacheService,
            MarketNewsFileCacheService marketNewsFileCacheService,
            NaverNewsApiClient naverNewsApiClient,
            GoogleNewsApiClient googleNewsApiClient,
            KisStockApiClient kisStockApiClient,
            KisTokenManager kisTokenManager,
            OpenAiDirectClient openAiDirectClient,
            NaverStockBoardClient naverStockBoardClient,
            HanwhaResearchListClient hanwhaResearchListClient,
            HanwhaResearchDailyCacheService hanwhaResearchDailyCacheService,
            @Qualifier(TutorialFetchAsyncConfig.TUTORIAL_FETCH_EXECUTOR) Executor tutorialFetchExecutor
    ) {
        this.dartApiClient = dartApiClient;
        this.dartDailyFileCacheService = dartDailyFileCacheService;
        this.marketNewsFileCacheService = marketNewsFileCacheService;
        this.naverNewsApiClient = naverNewsApiClient;
        this.googleNewsApiClient = googleNewsApiClient;
        this.kisStockApiClient = kisStockApiClient;
        this.kisTokenManager = kisTokenManager;
        this.openAiDirectClient = openAiDirectClient;
        this.naverStockBoardClient = naverStockBoardClient;
        this.hanwhaResearchListClient = hanwhaResearchListClient;
        this.hanwhaResearchDailyCacheService = hanwhaResearchDailyCacheService;
        this.tutorialFetchExecutor = tutorialFetchExecutor;
    }

    public AiTutorialResponse generateWhyApproachTutorial(
            String stockCode,
            String dartCode,
            String corpName,
            String market,
            String today,
            boolean naverBoardScrapeEnabled
    ) {
        log.info("{} 종목에 대한 실시간 AI 튜토리얼 분석 시작 (naverBoard={})", corpName, naverBoardScrapeEnabled);

        String validAccessToken = kisTokenManager.getAccessToken();
        String newsKeyword = corpName + " 주식";
        int naverStockN = clampDisplayCount(naverNewsStockDisplayCount, 1, 100);
        int googleStockN = clampDisplayCount(googleNewsStockDisplayCount, 1, 50);
        String mktTrim = market == null ? "" : market.trim();
        String marketNewsKeyword = mktTrim.isEmpty() ? "" : (mktTrim + " 증시");
        int naverMktN = clampDisplayCount(naverNewsMarketDisplayCount, 1, 100);
        int googleMktN = clampDisplayCount(googleNewsMarketDisplayCount, 1, 50);
        String code6 = stockCode != null ? stockCode.trim() : "";

        CompletableFuture<KisMarketMetrics> kisFuture = CompletableFuture.supplyAsync(
                () -> fetchKisMetricsParallel(stockCode, validAccessToken),
                tutorialFetchExecutor);

        CompletableFuture<DartCollectResult> dartFuture = CompletableFuture.supplyAsync(
                () -> loadDartCollectParallel(dartCode, today),
                tutorialFetchExecutor);

        CompletableFuture<NaverNewsResponse> naverStockFuture = CompletableFuture.supplyAsync(
                () -> naverNewsApiClient.searchNews(newsKeyword, naverStockN),
                tutorialFetchExecutor);

        CompletableFuture<List<RssNewsItem>> googleStockFuture = CompletableFuture.supplyAsync(
                () -> googleNewsApiClient.searchNewsItems(newsKeyword, googleStockN),
                tutorialFetchExecutor);

        CompletableFuture<MarketNewsPair> marketFuture = CompletableFuture.supplyAsync(
                () -> loadMarketNewsParallel(mktTrim, marketNewsKeyword, naverMktN, googleMktN),
                tutorialFetchExecutor);

        CompletableFuture<List<HanwhaResearchListItem>> hanwhaFuture = CompletableFuture.supplyAsync(
                () -> hanwhaResearchEnabled
                        ? hanwhaResearchDailyCacheService.getOrLoadToday(hanwhaResearchListClient::fetchFirstPage)
                        : List.of(),
                tutorialFetchExecutor);

        CompletableFuture<BoardScrapeResult> boardFuture = CompletableFuture.supplyAsync(
                () -> scrapeBoardParallel(stockCode, naverBoardScrapeEnabled),
                tutorialFetchExecutor);

        CompletableFuture.allOf(
                kisFuture,
                dartFuture,
                naverStockFuture,
                googleStockFuture,
                marketFuture,
                hanwhaFuture,
                boardFuture
        ).join();

        KisMarketMetrics kisMetrics = kisFuture.join();
        String parsedStockStatus = kisMetrics.llmContextBlock();

        DartCollectResult dart = dartFuture.join();
        DisclosureResponse disclosureData = dart.disclosureData();
        FinancialResponse financialData = dart.financialData();
        OwnershipResponse ownershipData = dart.ownershipData();
        DisclosureResponse majorIssuesData = dart.majorIssuesData();
        String documentSummary = dart.documentSummary();

        String parsedDisclosures = extractDisclosures(disclosureData);
        String parsedFinancials = extractFinancials(financialData);
        String ownershipLlmBlock = buildOwnershipLlmBlock(ownershipData);
        String parsedMajorIssues = extractDisclosures(majorIssuesData);

        NaverNewsResponse naverData = naverStockFuture.join();
        List<RssNewsItem> googleRssKeyword = googleStockFuture.join();
        String parsedNaverNews = extractNaverNews(naverData);
        List<String> googleNewsList = googleRssKeyword.stream()
                .map(i -> "- [구글] " + i.title() + " (" + i.pubDate() + ")")
                .collect(Collectors.toList());
        String parsedGoogleNews = String.join("\n", googleNewsList);

        MarketNewsPair marketPair = marketFuture.join();
        NaverNewsResponse naverMarketData = marketPair.naverMarket();
        List<RssNewsItem> googleRssMarket = marketPair.googleMarket();
        String parsedNaverNewsMarket = marketNewsKeyword.isBlank()
                ? "(시장 구분 없음 — 시장 키워드 뉴스 생략)"
                : extractNaverNews(naverMarketData);
        List<String> googleNewsListMarket = googleRssMarket.stream()
                .map(i -> "- [구글] " + i.title() + " (" + i.pubDate() + ")")
                .collect(Collectors.toList());
        String parsedGoogleNewsMarket = marketNewsKeyword.isBlank()
                ? "(시장 구분 없음 — 시장 키워드 뉴스 생략)"
                : String.join("\n", googleNewsListMarket);

        List<HanwhaResearchListItem> hanwhaResearchItems = hanwhaFuture.join();
        List<HanwhaResearchListItem> hanwhaResearchForStock = hanwhaResearchEnabled
                ? filterHanwhaItemsMatchingStockCode(code6, hanwhaResearchItems)
                : List.of();

        BoardScrapeResult boardResult = boardFuture.join();
        List<StockBoardPost> boardPosts = boardResult.posts();
        String parsedBoardPosts = boardResult.parsedPosts();
        String boardSentimentHint = boardResult.sentimentHint();
        if (!naverBoardScrapeEnabled) {
            log.info("[네이버종목토론] 사용자 설정으로 스크래핑 생략");
        }

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
        log.info("[한화WM 리서치] 원본 {}건, 종목코드 매칭 {}건 — {}", hanwhaResearchItems.size(), hanwhaResearchForStock.size(),
                hanwhaResearchEnabled ? "스냅샷" : "비활성화");
        if (naverBoardScrapeEnabled) {
            log.info("[네이버종목토론] 요약 - {}", boardSentimentHint);
        }

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
                hanwhaResearchForStock,
                boardPosts,
                boardSentimentHint
        );

        boolean hanwhaResearchInLlm = !hanwhaResearchForStock.isEmpty();
        String otherChannelsWhenHanwhaMissing = naverBoardScrapeEnabled
                ? "시세·공시·뉴스·토론은 포함됨."
                : "시세·공시·뉴스는 포함됨(종목토론은 요청에서 생략).";
        String parsedHanwhaResearch = !hanwhaResearchEnabled
                ? "(한화 WM 리서치 수집 비활성화)"
                : (hanwhaResearchInLlm
                        ? buildHanwhaResearchLlmBlock(hanwhaResearchForStock)
                        : "(당일 1페이지 목록에 해당 종목코드가 제목·요약·링크에 포함된 한화 WM 리서치가 없어 AI 입력에서 제외함. "
                                + otherChannelsWhenHanwhaMissing + ")");
        if (hanwhaResearchEnabled && !hanwhaResearchInLlm) {
            log.info("한화 WM 리서치에 종목코드 {} 미포함 — LLM 입력에서 리서치 블록만 제외, OpenAI 호출 유지", code6);
        }

        // 5. WHY Approach 시스템 프롬프트 조립
        String systemPrompt = buildWhyApproachSystemPrompt(hanwhaResearchEnabled, hanwhaResearchInLlm, naverBoardScrapeEnabled);

        String whyApproachQuestions = buildWhyApproachQuestions(hanwhaResearchInLlm, naverBoardScrapeEnabled);

        String communityBlockForPrompt = naverBoardScrapeEnabled
                ? """
            10. 여론 요약(추천/비추천 합계 기반 참고): %s
            %s
            """.formatted(boardSentimentHint, parsedBoardPosts)
                : """
            10. (네이버 종목토론방: 요청에 따라 수집·입력 생략)
            """;

        String userPromptHead = String.format("""
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
            
            [증권사 리서치 — 한화투자증권 WM 기업·산업분석, 당일 1페이지 중 해당 종목코드가 제목·요약·URL에 포함된 항목만]
            9. 리서치 제목·요약:
            %s
            
            [커뮤니티 반응 — 네이버 종목토론방]
            """, parsedStockStatus, parsedDisclosures, parsedFinancials, documentSummary,
                ownershipSectionForPrompt(ownershipLlmBlock),
                parsedNaverNews, parsedGoogleNews, parsedNaverNewsMarket, parsedGoogleNewsMarket,
                parsedHanwhaResearch);
        String userPrompt = userPromptHead + communityBlockForPrompt + "\n\n" + whyApproachQuestions;

        String summary = openAiDirectClient.requestChatCompletion(systemPrompt, userPrompt);
        return new AiTutorialResponse(summary, evidence);
    }

    private static String buildWhyApproachQuestions(boolean hanwhaResearchInLlm, boolean naverBoardInLlm) {
        if (hanwhaResearchInLlm && naverBoardInLlm) {
            return """
                    위 사실을 바탕으로:
                    - 거래 활발도(거래량·대금·회전·체결강도)와 밸류에이션 맥락은 어떤가?
                    - 공시나 뉴스가 시장 분위기에 어떤 배경을 주고 있는가?
                    - 한화 WM 리서치 목록이 오늘의 시장/섹터 분위기와 어떻게 겹쳐 보이는지 한 문장(해당 없으면 생략)?
                    - 네이버 종목토론방에서는 긍정/부정적 반응이 어떻게 보이는지 한 문장으로?
                    를 3~4줄로 쉽게 설명해 줘. (등락률·현재가·%% 수치는 언급하지 말 것.)
                    """;
        }
        if (hanwhaResearchInLlm) {
            return """
                    위 사실을 바탕으로:
                    - 거래 활발도(거래량·대금·회전·체결강도)와 밸류에이션 맥락은 어떤가?
                    - 공시나 뉴스가 시장 분위기에 어떤 배경을 주고 있는가?
                    - 한화 WM 리서치 목록이 오늘의 시장/섹터 분위기와 어떻게 겹쳐 보이는지 한 문장(해당 없으면 생략)?
                    를 3~4줄로 쉽게 설명해 줘. (등락률·현재가·%% 수치는 언급하지 말 것. 네이버 종목토론은 입력에 없으므로 커뮤니티 반응에 대해서는 언급하지 말 것.)
                    """;
        }
        if (naverBoardInLlm) {
            return """
                    위 사실을 바탕으로:
                    - 거래 활발도(거래량·대금·회전·체결강도)와 밸류에이션 맥락은 어떤가?
                    - 공시나 뉴스가 시장 분위기에 어떤 배경을 주고 있는가?
                    - 네이버 종목토론방에서는 긍정/부정적 반응이 어떻게 보이는지 한 문장으로?
                    를 3~4줄로 쉽게 설명해 줘. (등락률·현재가·%% 수치는 언급하지 말 것. 한화 WM 리서치는 입력에 없으므로 증권사 리서치 목록에 대해서는 언급하지 말 것.)
                    """;
        }
        return """
                위 사실을 바탕으로:
                - 거래 활발도(거래량·대금·회전·체결강도)와 밸류에이션 맥락은 어떤가?
                - 공시나 뉴스가 시장 분위기에 어떤 배경을 주고 있는가?
                를 3~4줄로 쉽게 설명해 줘. (등락률·현재가·%% 수치는 언급하지 말 것. 한화 WM 리서치는 입력에 없으며 증권사 리서치에 대해 언급하지 말 것. 네이버 종목토론은 입력에 없으며 커뮤니티 반응에 대해 언급하지 말 것.)
                """;
    }

    private static String buildWhyApproachSystemPrompt(
            boolean hanwhaResearchEnabled,
            boolean hanwhaResearchInLlm,
            boolean naverBoardInLlm
    ) {
        String dataScopeLine = naverBoardInLlm
                ? """
                입력 데이터에는 등락률·현재가·52주 대비 등이 포함되어 있지 않습니다. **전일 대비 몇 % 올랐다/내렸다, 현재가, 52주 대비 몇 %** 같은 표현은 하지 마세요. 거래량·거래대금·회전율·체결강도·PER·RSI·공시·뉴스·토론 분위기 위주로 맥락을 설명하세요.
                """
                : """
                입력 데이터에는 등락률·현재가·52주 대비 등이 포함되어 있지 않습니다. **전일 대비 몇 % 올랐다/내렸다, 현재가, 52주 대비 몇 %** 같은 표현은 하지 마세요. 거래량·거래대금·회전율·체결강도·PER·RSI·공시·뉴스 위주로 맥락을 설명하세요.
                """;
        String communityLine = naverBoardInLlm
                ? """
                네이버 종목토론방 글은 개인 의견·감정에 가깝고 추천/비추천 수도 참고용입니다. 여론을 사실처럼 단정하지 말고, '커뮤니티에서는 이런 분위기가 보인다' 수준으로 짧게 언급하세요.
                """
                : """
                오늘 입력에는 네이버 종목토론방 데이터가 포함되어 있지 않습니다. 토론방·커뮤니티 반응에 대해서는 설명하지 마세요.
                """;
        String common = """
                당신은 주식 투자를 처음 시작하는 사람들에게 단기간 종목이 왜 움직였는지를 알려주는 가이드입니다.
                많은 사람들이 주식 가격이 왜 움직였는지 이해를 돕는 것이 우리의 가장 큰 목표입니다.
                사용자들은 지식의 편차가 심하므로, 전문 용어를 최대한 배제하고 쉽게 비유해서 설명해야 합니다.
                """ + dataScopeLine + """
                제공된 '시장 참고 수치'를 먼저 짚어주고, 공시와 뉴스를 바탕으로 분위기와 배경을 인과관계로 설명하세요.
                PER·PBR·RSI·체결강도 등은 증권사 API가 준 참고 수치일 뿐이며, 투자의 정답이나 매수·매도 신호가 아님을 유의해 설명하세요.
                """ + communityLine;
        String hanwhaWhenPresent = """
                한화투자증권 WM '기업·산업분석'으로 넘어온 항목은 당일 1페이지 목록 중 해당 종목의 6자리 코드가 제목·요약·URL에 포함된 것만입니다. 투자 권유나 매수·매도 의견이 아님을 밝히고, 제목·요약 수준만 배경 참고로 짧게 언급하세요.
                """;
        String hanwhaOmitted = """
                오늘 사용자 데이터에는 한화투자증권 WM '기업·산업분석' 리서치 목록 본문이 포함되어 있지 않습니다(당일 1페이지에 해당 종목코드가 없음). 한화·증권사 리서치 목록이나 리포트에 대해서는 설명하지 마세요.
                """;
        String closing = """
                사용자가 스스로 시장을 이해하고 당사 MTS에 계속 머물며 학습하고 싶도록 작성해 주세요.
                """;
        if (!hanwhaResearchEnabled) {
            return common + hanwhaWhenPresent + closing;
        }
        if (hanwhaResearchInLlm) {
            return common + hanwhaWhenPresent + closing;
        }
        return common + hanwhaOmitted + closing;
    }

    /**
     * 당일 한화 WM 1페이지 목록 중, 제목·요약·링크에 6자리 단축코드가 포함된 항목만 반환합니다.
     */
    private static List<HanwhaResearchListItem> filterHanwhaItemsMatchingStockCode(
            String stockCode6,
            List<HanwhaResearchListItem> items
    ) {
        if (stockCode6 == null || !stockCode6.matches("^\\d{6}$") || items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(i -> fieldContainsStockCode(stockCode6, i.title())
                        || fieldContainsStockCode(stockCode6, i.snippet())
                        || fieldContainsStockCode(stockCode6, i.link()))
                .collect(Collectors.toList());
    }

    private static boolean fieldContainsStockCode(String code6, String text) {
        return text != null && text.contains(code6);
    }

    private static int clampDisplayCount(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private KisMarketMetrics fetchKisMetricsParallel(String stockCode, String token) {
        try {
            CompletableFuture<KisStockResponse> priceF = CompletableFuture.supplyAsync(
                    () -> kisStockApiClient.getCurrentPrice(stockCode, token),
                    tutorialFetchExecutor);
            CompletableFuture<KisInvestmentIndicatorResponse> investF = CompletableFuture.supplyAsync(
                    () -> kisStockApiClient.getInvestmentIndicator(stockCode, token),
                    tutorialFetchExecutor);
            CompletableFuture<KisCcnlResponse> ccnlF = CompletableFuture.supplyAsync(
                    () -> kisStockApiClient.getTimeConclusion(stockCode, token),
                    tutorialFetchExecutor);
            CompletableFuture.allOf(priceF, investF, ccnlF).join();
            return buildKisMarketMetrics(priceF.join(), investF.join(), ccnlF.join());
        } catch (Exception e) {
            log.warn("[KIS] 병렬 수집 실패, 기본값 사용: {}", e.getMessage());
            return buildKisMarketMetrics(null, null, null);
        }
    }

    private DartCollectResult loadDartCollectParallel(String dartCode, String today) {
        CompletableFuture<DisclosureResponse> discF = CompletableFuture.supplyAsync(
                () -> dartDailyFileCacheService.loadDisclosureList(
                        dartCode, today, today, "1", "10",
                        () -> dartApiClient.searchDisclosures(dartCode, today, today, "1", "10")),
                tutorialFetchExecutor);
        CompletableFuture<FinancialResponse> finF = CompletableFuture.supplyAsync(
                () -> dartDailyFileCacheService.loadFinancial(
                        dartCode, "2023", "11011",
                        () -> dartApiClient.getFinancialInfo(dartCode, "2023", "11011")),
                tutorialFetchExecutor);
        CompletableFuture<OwnershipResponse> ownF = CompletableFuture.supplyAsync(
                () -> dartDailyFileCacheService.loadOwnership(
                        dartCode, () -> dartApiClient.getOwnershipDisclosures(dartCode)),
                tutorialFetchExecutor);
        CompletableFuture<DisclosureResponse> majF = CompletableFuture.supplyAsync(
                () -> dartDailyFileCacheService.loadMajorIssues(
                        dartCode, today, today,
                        () -> dartApiClient.getMajorManagementIssues(dartCode, today, today)),
                tutorialFetchExecutor);
        CompletableFuture.allOf(discF, finF, ownF, majF).join();

        DisclosureResponse disclosureData = discF.join();
        FinancialResponse financialData = finF.join();
        OwnershipResponse ownershipData = ownF.join();
        DisclosureResponse majorIssuesData = majF.join();

        String documentSummary = "최신 원문 없음";
        if (disclosureData != null && disclosureData.list() != null && !disclosureData.list().isEmpty()) {
            String rceptNo = disclosureData.list().get(0).rceptNo();
            byte[] zipData = dartDailyFileCacheService.loadDisclosureDocument(
                    rceptNo, () -> dartApiClient.getDisclosureDocument(rceptNo));
            documentSummary = extractTextFromZip(zipData);
        }
        return new DartCollectResult(disclosureData, financialData, ownershipData, majorIssuesData, documentSummary);
    }

    private MarketNewsPair loadMarketNewsParallel(
            String mktTrim,
            String marketNewsKeyword,
            int naverMktN,
            int googleMktN
    ) {
        if (marketNewsKeyword.isBlank()) {
            return new MarketNewsPair(null, Collections.emptyList());
        }
        var marketCached = marketNewsFileCacheService.readIfValid(mktTrim);
        if (marketCached.isPresent()) {
            return new MarketNewsPair(marketCached.get().naverMarket(), marketCached.get().googleMarket());
        }
        CompletableFuture<NaverNewsResponse> naverF = CompletableFuture.supplyAsync(
                () -> naverNewsApiClient.searchNews(marketNewsKeyword, naverMktN),
                tutorialFetchExecutor);
        CompletableFuture<List<RssNewsItem>> googleF = CompletableFuture.supplyAsync(
                () -> googleNewsApiClient.searchNewsItems(mktTrim, googleMktN),
                tutorialFetchExecutor);
        CompletableFuture.allOf(naverF, googleF).join();
        NaverNewsResponse naverMarketData = naverF.join();
        List<RssNewsItem> googleRssMarket = googleF.join();
        marketNewsFileCacheService.put(mktTrim, naverMarketData, googleRssMarket);
        return new MarketNewsPair(naverMarketData, googleRssMarket);
    }

    private BoardScrapeResult scrapeBoardParallel(String stockCode, boolean naverBoardScrapeEnabled) {
        if (!naverBoardScrapeEnabled) {
            return new BoardScrapeResult(
                    List.of(),
                    "(네이버 종목토론방 수집 생략 — 요청에서 제외)",
                    "");
        }
        List<StockBoardPost> boardPosts = naverStockBoardClient.fetchLatestPosts(stockCode, 5);
        String parsedBoardPosts = formatNaverBoardPosts(boardPosts);
        String boardSentimentHint = summarizeBoardSentiment(boardPosts);
        return new BoardScrapeResult(boardPosts, parsedBoardPosts, boardSentimentHint);
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
            List<HanwhaResearchListItem> hanwhaResearchItems,
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
                toHanwhaResearchRefs(hanwhaResearchItems),
                toBoardRefs(boardPosts),
                boardSentimentHint,
                tutorialMarketDataProviderLabel,
                tutorialResearchProviderLabel
        );
    }

    private List<HanwhaResearchRef> toHanwhaResearchRefs(List<HanwhaResearchListItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(i -> new HanwhaResearchRef(
                        i.title(),
                        i.link() != null ? i.link() : "",
                        i.category() != null ? i.category() : "",
                        i.author() != null ? i.author() : "",
                        i.publishedAt() != null ? i.publishedAt() : "",
                        i.snippet() != null ? i.snippet() : ""
                ))
                .collect(Collectors.toList());
    }

    private static String buildHanwhaResearchLlmBlock(List<HanwhaResearchListItem> items) {
        if (items == null || items.isEmpty()) {
            return "(한화 WM 리서치 목록 없음 또는 수집 생략)";
        }
        return items.stream()
                .limit(15)
                .map(i -> "- [" + nullToEmpty(i.category()) + "] " + nullToEmpty(i.title())
                        + " | " + truncate(nullToEmpty(i.snippet()), 220))
                .collect(Collectors.joining("\n"));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
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

    private record DartCollectResult(
            DisclosureResponse disclosureData,
            FinancialResponse financialData,
            OwnershipResponse ownershipData,
            DisclosureResponse majorIssuesData,
            String documentSummary
    ) {
    }

    private record MarketNewsPair(NaverNewsResponse naverMarket, List<RssNewsItem> googleMarket) {
    }

    private record BoardScrapeResult(
            List<StockBoardPost> posts,
            String parsedPosts,
            String sentimentHint
    ) {
    }
}
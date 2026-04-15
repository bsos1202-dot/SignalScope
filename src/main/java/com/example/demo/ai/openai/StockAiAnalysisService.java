package com.example.demo.ai.openai;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.example.demo.collect.dart.DartApiClient;
import com.example.demo.collect.dart.dto.DisclosureResponse;
import com.example.demo.collect.dart.dto.FinancialResponse;
import com.example.demo.collect.dart.dto.OwnershipResponse;
import com.example.demo.collect.kis.KisStockApiClient;
import com.example.demo.collect.kis.KisTokenManager;
import com.example.demo.collect.kis.dto.KisStockResponse;
import com.example.demo.collect.news.GoogleNewsApiClient;
import com.example.demo.collect.news.NaverNewsApiClient;
import com.example.demo.collect.news.dto.NaverNewsResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockAiAnalysisService {

    private final DartApiClient dartApiClient;
    private final NaverNewsApiClient naverNewsApiClient;
    private final GoogleNewsApiClient googleNewsApiClient;
    private final KisStockApiClient kisStockApiClient;
    private final KisTokenManager kisTokenManager;
    private final OpenAiDirectClient openAiDirectClient;

    public String generateWhyApproachTutorial(String stockCode, String dartCode, String corpName, String market, String today) {
        log.info("{} 종목에 대한 실시간 AI 튜토리얼 분석 시작", corpName);

        // 1. 시장 현재 상태 수집 (한국투자증권 실시간 시세 - 6자리 코드 사용)
        String validAccessToken = kisTokenManager.getAccessToken(); 
        KisStockResponse stockData = kisStockApiClient.getCurrentPrice(stockCode, validAccessToken);
        String parsedStockStatus = extractStockStatus(stockData);

        // 2. 팩트 데이터 수집 (DART - 8자리 고유번호 사용)
        DisclosureResponse disclosureData = dartApiClient.searchDisclosures(dartCode, today, today, "1", "10");
        FinancialResponse financialData = dartApiClient.getFinancialInfo(dartCode, "2023", "11011");
        OwnershipResponse ownershipData = dartApiClient.getOwnershipDisclosures(dartCode);
        DisclosureResponse majorIssuesData = dartApiClient.getMajorManagementIssues(dartCode, today, today);

        // 공시 상세 원문(ZIP) 수집 및 텍스트 파싱
        String documentSummary = "최신 원문 없음";
        if (disclosureData != null && disclosureData.list() != null && !disclosureData.list().isEmpty()) {
            String rceptNo = disclosureData.list().get(0).rceptNo(); 
            byte[] zipData = dartApiClient.getDisclosureDocument(rceptNo);
            documentSummary = extractTextFromZip(zipData);
        }

        String parsedDisclosures = extractDisclosures(disclosureData);
        String parsedFinancials = extractFinancials(financialData);
        String parsedOwnership = extractOwnerships(ownershipData);
        String parsedMajorIssues = extractDisclosures(majorIssuesData); // 재사용

        // 3. 시장 반응 데이터 수집 (멀티 채널 뉴스 - 종목명 기반 검색)
        String newsKeyword = corpName + " 주식";
        
        NaverNewsResponse naverData = naverNewsApiClient.searchNews(newsKeyword, 3);
        String parsedNaverNews = extractNaverNews(naverData);
        
        List<String> googleNewsList = googleNewsApiClient.searchNews(newsKeyword, 5);
        String parsedGoogleNews = String.join("\n", googleNewsList);
        
        List<String> googleNewsListMarket = googleNewsApiClient.searchNews(market, 3);
        String parsedGoogleNewsMarket = String.join("\n", googleNewsListMarket);
        
        log.info("[한국투자증권] 주가정보 - " + parsedStockStatus);
        log.info("[DART] 공시정보 - " + parsedFinancials);
        log.info("[DART] 소유정보 - " + parsedOwnership);
        log.info("[DART] 주요정보 - " + parsedMajorIssues);
        log.info("[네이버API] 뉴스 - " + parsedNaverNews);
        log.info("[구글RSS] 뉴스 - " + parsedGoogleNews);
        log.info("[구글RSS] 뉴스 - " + parsedGoogleNewsMarket);

        // 4. WHY Approach 시스템 프롬프트 조립
        String systemPrompt = """
            당신은 주식 투자를 처음 시작하는 사람들에게 단기간 종목이 왜 움직였는지를 알려주는 가이드입니다.
            많은 사람들이 주식 가격이 왜 움직였는지 이해를 돕는 것이 우리의 가장 큰 목표입니다.
            사용자들은 지식의 편차가 심하므로, 전문 용어를 최대한 배제하고 쉽게 비유해서 설명해야 합니다.
            제공된 '현재 주가 상황'을 먼저 짚어주고, 공시와 뉴스를 바탕으로 그 주가가 왜 그렇게 움직이고 있는지 인과관계를 설명하세요.
            현재가는 민감한 정보이니 명시하지 않고 변동율만 노출해줘(인과관계 상 필요없다면 노출 안해도 되).
            사용자가 스스로 시장을 이해하고 당사 MTS에 계속 머물며 학습하고 싶도록 작성해 주세요.
            """;

        String userPrompt = String.format("""
            다음은 특정 기업의 실시간 데이터입니다.
            
            [현재 시장 상황]
            1. 실시간 시세: %s
            
            [핵심 팩트 데이터]
            2. 발생한 공시: %s
            3. 재무 지표: %s
            4. 공시 상세 원문 요약: %s
            
            [시장 반응 데이터]
            5. 네이버 뉴스 동향:
            %s
            6. 구글 뉴스 동향:
            %s
            %s
            
            위 사실을 바탕으로:
            - 지금 이 종목의 현재 가격과 분위기는 어떤 상태인지?
            - 공시나 뉴스가 이 가격 움직임에 '왜' 영향을 주고 있는지?
            를 3줄로 쉽게 설명해 줘.
            """, parsedStockStatus, parsedDisclosures, parsedFinancials, documentSummary, parsedNaverNews, parsedGoogleNews, parsedGoogleNewsMarket);

        return openAiDirectClient.requestChatCompletion(systemPrompt, userPrompt);
    }

    // --- 데이터 추출 헬퍼 메서드들 ---

    private String extractStockStatus(KisStockResponse data) {
        if (data == null || data.output() == null) {
            return "- 현재 시세 정보를 불러올 수 없습니다.";
        }
        String price = formatNumber(data.output().currentPrice());
        String rate = data.output().changeRate();
        String volume = formatNumber(data.output().volume());
        return String.format("현재가 %s원 (전일 대비 %s%% 변동), 오늘 거래량은 %s주 입니다.", price, rate, volume);
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

    private String extractOwnerships(OwnershipResponse data) {
        if (data == null || data.list() == null || data.list().isEmpty()) return "- 지분 변동 없음";
        return data.list().stream()
                .limit(5)
                .map(item -> "- 보고자: " + item.repror() + ", 지분율: " + item.ownershipRate() + "%")
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
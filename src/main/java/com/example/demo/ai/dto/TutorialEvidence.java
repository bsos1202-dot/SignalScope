package com.example.demo.ai.dto;

import java.util.List;

/**
 * AI 요약에 참조된 데이터 요약(보조지표·근거 목록).
 */
public record TutorialEvidence(
        String stockQuoteLine,
        KisMarketMetrics kisMarketMetrics,
        List<DisclosureRef> dartDisclosures,
        List<DisclosureRef> dartMajorIssues,
        List<String> financialLines,
        List<String> ownershipLines,
        String disclosureDocumentSnippet,
        List<NewsRef> naverNews,
        List<NewsRef> googleNews,
        List<NewsRef> naverNewsMarket,
        List<NewsRef> googleNewsMarket,
        List<HanwhaResearchRef> hanwhaResearch,
        List<BoardRef> boardPosts,
        String boardSentimentHint
) {
    public record DisclosureRef(String receiptDate, String reportName, String receiptNo) {}

    public record NewsRef(String title, String link, String publishedAt, String description) {}

    /** 한화 WM 기업·산업분석 목록 1페이지 스냅샷(스크래핑). */
    public record HanwhaResearchRef(String title, String link, String category, String author, String publishedAt, String snippet) {}

    public record BoardRef(String postedAt, String title, String url, int upvotes, int downvotes, String author) {}
}

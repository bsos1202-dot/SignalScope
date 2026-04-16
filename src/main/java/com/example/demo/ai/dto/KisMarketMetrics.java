package com.example.demo.ai.dto;

/**
 * 한국투자증권 API 기반 시세·밸류에이션·기술·체결 지표 (분석 입력용).
 */
public record KisMarketMetrics(
        String headlineQuote,
        String per,
        String pbr,
        String eps,
        String bps,
        String rsi,
        String volumeTurnoverRate,
        String w52HighVsCurrentPct,
        String w52LowVsCurrentPct,
        String foreignHoldingRatio,
        String contractStrength,
        String contractStrengthTime,
        /** LLM 입력에 넣는 통합 문단 */
        String llmContextBlock
) {}

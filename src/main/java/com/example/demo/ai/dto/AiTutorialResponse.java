package com.example.demo.ai.dto;

/**
 * 종목 튜토리얼: LLM 요약 본문 + 참조 근거.
 */
public record AiTutorialResponse(String summary, TutorialEvidence evidence) {}

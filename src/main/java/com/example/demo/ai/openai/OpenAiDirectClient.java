package com.example.demo.ai.openai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiDirectClient {

    private final RestTemplate restTemplate;

    @Value("${openai.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${openai.api-key}")
    private String apiKey;

    /**
     * OpenAI REST API를 직접 호출합니다.
     */
    public String requestChatCompletion(String systemPrompt, String userPrompt) {
        // 1. HTTP 헤더 설정 (인증 토큰 및 컨텐츠 타입)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // 2. 요청 본문(Body) JSON 구조 만들기
        Map<String, Object> requestBody = new HashMap<>();
        //requestBody.put("model", "gpt-4o"); // 사용할 모델 명시
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("temperature", 0.3); // 사실 기반의 분석을 위해 창의성 낮춤

        // 메시지 구성 (시스템 역할 + 사용자 역할)
        Map<String, String> systemMessage = Map.of("role", "system", "content", systemPrompt);
        Map<String, String> userMessage = Map.of("role", "user", "content", userPrompt);
        requestBody.put("messages", List.of(systemMessage, userMessage));

        // 3. HTTP 엔티티 조립 및 POST 요청
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        try {
            log.info("OpenAI API 직접 호출 시작...");
            // 응답을 범용적인 Map 형태로 받습니다.
            Map response = restTemplate.postForObject(apiUrl, entity, Map.class);
            
            // 4. 응답 JSON에서 실제 답변 텍스트만 추출
            return extractContentFromResponse(response);

        } catch (Exception e) {
            log.error("OpenAI API 호출 중 오류 발생: ", e);
            return "AI 분석을 불러오는 중 오류가 발생했습니다.";
        }
    }

    /**
     * OpenAI 응답 Map에서 choices[0].message.content 구조를 타고 들어가 텍스트를 꺼냅니다.
     */
    private String extractContentFromResponse(Map response) {
        if (response == null || !response.containsKey("choices")) return "";

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices.isEmpty()) return "";

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
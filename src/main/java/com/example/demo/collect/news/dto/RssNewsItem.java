package com.example.demo.collect.news.dto;

/**
 * 구글 뉴스 RSS에서 뽑은 한 건.
 */
public record RssNewsItem(String title, String link, String pubDate) {}

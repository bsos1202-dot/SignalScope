package com.example.demo.collect.naverboard.dto;

/**
 * 네이버 금융 종목토론 게시판 목록 한 행.
 */
public record StockBoardPost(
        String postedAt,
        String title,
        String url,
        String author,
        int views,
        int upvotes,
        int downvotes
) {}

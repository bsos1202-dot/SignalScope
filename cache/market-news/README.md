# 시장 키워드 뉴스 캐시 (`cache/market-news`)

코스피·코스닥 등 **시장명 단위**로 네이버 뉴스 API + 구글 RSS 결과를 한 파일에 저장합니다.  
튜토리얼 종목 캐시(`cache/tutorial`)와 **분리**되어 있으며, TTL(기본 30분) 동안 **모든 종목**이 동일 파일을 읽습니다.

- 파일명: `{슬러그}.json` — 예: `KOSPI.json`, `KOSDAQ.json` (`app.market-news-cache.directory`)
- 설정: `app.market-news-cache.ttl-minutes`, `app.market-news-cache.enabled`
- 무효화: 해당 JSON 삭제 또는 TTL 만료 대기

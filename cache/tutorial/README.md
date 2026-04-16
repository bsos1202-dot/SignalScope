# AI 튜토리얼 파일 캐시 (`cache/tutorial`)

종목(6자리 단축코드)별로 `/ai/tutorial` 응답 스냅샷을 **JSON 파일**로 저장합니다. 기본 **TTL 10분** 동안 같은 종목 재요청 시 외부 API·OpenAI를 다시 호출하지 않고 이 디렉터리의 파일을 반환합니다.

## 파일 형식

- 파일명: `{종목코드}.json` (예: `005930.json`)
- 내용: `TutorialCacheFile` — `version`, `stockCode`, `corpName`, `market`, `createdAtMillis`, `expiresAtMillis`, `response` (`AiTutorialResponse`)

## 운영 시 유의사항

1. **디스크 용량**: 종목 수만큼 파일이 생길 수 있습니다. TTL이 지난 파일은 다음 요청 시 덮어쓰기되며, 배치로 오래된 파일을 지우는 스크립트를 두는 것을 권장합니다.
2. **경로 설정**: `application.properties`의 `app.tutorial-cache.directory`로 디렉터리를 바꿀 수 있습니다(컨테이너·NAS 등 마운트 경로 권장).
3. **동시성**: 동일 종목에 대한 동시 쓰기는 임시 파일 후 `rename`으로 원자적 교체합니다.
4. **캐시 무효화**: 긴급 시 해당 `{코드}.json`을 삭제하면 다음 요청에서 재수집합니다.
5. **HTTP**: 캐시 적중 시 응답 헤더 `X-AIS-Tutorial-Cache: HIT`, 미적중 시 `MISS`입니다. 생성·만료 시각은 `X-AIS-Tutorial-Cache-Generated`, `X-AIS-Tutorial-Cache-Expires`(ISO-8601, UTC)를 참고하세요.

## Git

`*.json` 캐시 본문은 `.gitignore`에 포함되어 저장소에 올라가지 않습니다. 이 `README.md`만 추적됩니다.

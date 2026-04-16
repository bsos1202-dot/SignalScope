# AIS

국내 종목을 검색한 뒤, **실시간 시세·공시·뉴스·(선택) 커뮤니티 반응**을 모아 OpenAI로 **“왜 움직였는지(WHY)”**를 쉬운 말로 설명해 주는 Spring Boot 기반 데모 애플리케이션입니다.

> 본 프로젝트는 **투자 권유나 법적·회계적 조언이 아닙니다.** 참고용 설명 목적입니다.

## 주요 기능

- **종목 검색 UI** (`/search.html`): KOSPI/KOSDAQ 마스터 기반 자동완성, 선택 시 AI 튜토리얼 요청
- **종목 목록 API**: 인메모리 캐시에서 전체 종목 JSON 제공
- **AI 튜토리얼 API**: DART·한국투자증권 시세·뉴스·네이버 종목토론(스크래핑) 등을 조합해 텍스트 응답

## 기술 스택

- Java 17, Gradle, Spring Boot 4.x
- Spring Web (REST), Lombok, Jsoup(HTML 파싱)
- OpenAI Chat Completions API

## 사전 요구사항

- JDK 17 이상
- 아래 외부 서비스용 **API 키·앱키** (`.env`에 설정)

## 환경 변수

민감 값은 저장소에 넣지 말고, **프로젝트 루트의 `.env`** 파일에 두세요. `spring.config.import`로 읽습니다.

`.env.example`을 복사해 `.env`를 만든 뒤 값을 채웁니다.

| 변수 | 용도 |
|------|------|
| `API_DART_KEY` | 금융감독원 전자공시(DART) Open API |
| `API_NAVER_CLIENT_ID` | 네이버 검색 API(뉴스) |
| `API_NAVER_CLIENT_SECRET` | 네이버 검색 API(뉴스) |
| `OPENAI_API_KEY` | OpenAI API |
| `API_KIS_APP_KEY` | 한국투자증권 Open API |
| `API_KIS_APP_SECRET` | 한국투자증권 Open API |
| `API_DATA_GO_KR_SERVICE_KEY` | 공공데이터포털(예탁원 등 연동 시) |

`.env`는 `.gitignore`에 포함되어 커밋되지 않습니다.

## 실행 방법

```bash
# Windows
.\gradlew.bat bootRun

# 또는 테스트만
.\gradlew.bat test
```

기본 포트는 Spring Boot 기본값(**8080**)입니다. 브라우저에서 `http://localhost:8080/search.html` 로 검색 화면을 열 수 있습니다.

## HTTP 엔드포인트 (요약)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/stocks/all` | 종목 목록(JSON) |
| GET | `/ai/tutorial` | `corp_code`, `corp_name`, `market` 쿼리 — JSON: `summary`(LLM 본문), `evidence`(참조 공시·뉴스·종목토론 등) |

## 문서

- 아키텍처·데이터 흐름: [DESIGN.md](./DESIGN.md)

## 주의사항

- **네이버 종목토론방** 데이터는 공식 API가 아니라 **HTML 스크래핑**이며, 페이지 구조 변경 시 동작이 깨질 수 있습니다. 이용약관·robots 정책은 각 서비스 기준을 따르세요.
- `stock_list_cache.json` 등 로컬 캐시 파일은 환경에 따라 생성·갱신됩니다.

## 라이선스 및 기여

저장소 정책에 따릅니다. (미지정 시 사내/대회 규정을 우선하세요.)

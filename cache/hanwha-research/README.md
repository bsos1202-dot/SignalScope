# 한화 WM 리서치 목록 캐시 (`cache/hanwha-research`)

[한화투자증권 WM 기업·산업분석](https://www.hanwhawm.com/main/research/main/list.cmd?depth2_id=1002&mode=depth2) **목록 1페이지**를 스크래핑한 결과를 **KST 날짜**별 JSON으로 저장합니다. 같은 날에는 파일이 있으면 재스크래핑하지 않습니다.

- 파일명: `{yyyyMMdd}.json` (예: `20260416.json`)
- 설정: `app.hanwha-research.cache-directory`, `app.hanwha-research.cache-enabled`, 전체 끄기 `app.hanwha-research.enabled`
- 법무·약관: 상용 서비스 반영 전 내부 검토 권장. 무효화는 해당 일자 JSON 삭제.

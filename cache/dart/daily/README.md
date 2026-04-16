# DART 일 단위 파일 캐시 (`cache/dart/daily`)

**한국시간(KST) 날짜**별 하위 폴더에 DART JSON 응답과 공시 원문 ZIP을 저장합니다.  
같은 날·같은 종목(8자리 `corp_code`)·같은 쿼리 키에 대해 **디스크에 있으면 DART를 다시 호출하지 않습니다.**

- 경로 예: `cache/dart/daily/20260416/00126380_list_20260416_20260416_p1c10.json`
- 원문 ZIP: `.../doc_{접수번호14자리}.zip`
- 설정: `app.dart-cache.directory`, `app.dart-cache.enabled`
- 운영: 날짜 폴더가 누적되므로 주기적 삭제(보관 정책) 권장. 스키마 변경 시 해당 일 폴더만 지워도 됩니다.

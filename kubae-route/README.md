# 쿠배내비 Route V0.2.0

잘못 진행된 화면 공유/미러링 방식과 분리한 새 경로형 프로토타입입니다.

## 보호 원칙
- rider-jjakkung-app 수정 없음
- 기존 짝꿍내비 정상 코드 수정 없음
- kakao-quickmate-clean 수정 없음
- 홈페이지 main 수정 없음
- 이 브랜치의 kubae-route 폴더만 사용

## V0.2.0 핵심
- 보조폰 자체 지도(OSM/osmdroid)
- 보조폰 GPS 현재 위치
- 목적지 위도/경도 입력
- Valhalla motor_scooter 후보 경로 3개 계산
- use_primary 값을 0.00 / 0.15 / 0.35로 달리해 대도로 선호를 낮춘 후보 생성
- 후보 중 실제 route summary의 거리(km)가 가장 짧은 경로 선택
- 현재 위치 자동 추적
- 기존 짝꿍내비 연동용 Intent action 미리 정의:
  - action: com.kubaenavi.OPEN_ROUTE
  - extras: dest_lat, dest_lon
  - deeplink: kubaenavi://route?lat=...&lon=...

## 주의
Valhalla 공개 서버는 개발/검증용입니다. 최종 배포판은 자체 서버 또는 정식 운영 인프라가 필요합니다.

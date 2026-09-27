# 쿠배내비 V0.3.0 · 내비형 운행 화면

잘못 진행된 화면 공유/미러링 방식과 분리한 새 경로형 프로토타입입니다.

## 보호 원칙
- rider-jjakkung-app 수정 없음
- 기존 짝꿍내비 정상 코드 수정 없음
- kakao-quickmate-clean 수정 없음
- 홈페이지 main 수정 없음
- 이 브랜치의 kubae-route 폴더만 사용

## V0.2.1 핵심
- 보조폰 자체 지도(OSM/osmdroid)
- 보조폰 GPS 현재 위치
- 기존 짝꿍내비와 같은 목적지 주소 입력 + 위도/경도 수동 입력
- Valhalla motor_scooter 후보 경로 3개 계산
- use_primary 값을 0.00 / 0.15 / 0.35로 달리해 대도로 선호를 낮춘 후보 생성
- 후보 중 실제 route summary의 거리(km)가 가장 짧은 경로 선택
- 현재 위치 자동 추적
- 기존 짝꿍내비 연동용 Intent action 미리 정의:
  - action: com.kubaenavi.OPEN_ROUTE
  - extras: dest_address 또는 dest_lat, dest_lon
  - deeplink: kubaenavi://route?address=... 또는 lat/lon

## 주의
Valhalla 공개 서버는 개발/검증용입니다. 최종 배포판은 자체 서버 또는 정식 운영 인프라가 필요합니다.


## V0.3.0 추가
- 운행 시작 후 목적지 입력 카드 자동 숨김
- 전체화면 지도 중심 운행 UI
- 상단 다음 회전 방향/거리/안내
- 다다음 안내 미리보기
- 회전 80m 이내 중앙 큰 화살표
- GPS bearing 기반 진행방향 위쪽 자동 회전
- 회전거리/속도에 따른 자동 확대·축소
- 하단 도착예정시각/남은거리/남은시간
- 지도 수동 이동 시 자동추적 일시 중지, 현재위치 버튼으로 복귀

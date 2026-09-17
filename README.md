# 🏷️ Perfume Stalker (퍼퓸 스토커)

![Perfume Stalker](https://img.shields.io/badge/Version-1.0.0-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?logo=spring-boot&logoColor=white)
![Playwright](https://img.shields.io/badge/Playwright-Web_Scraping-2EAD33?logo=playwright&logoColor=white)
![Notion API](https://img.shields.io/badge/Database-Notion_API-000000?logo=notion&logoColor=white)

**Perfume Stalker**는 NFC 태그와 스마트폰을 활용하여, 향수 착향 기록을 단 1초 만에 남기고 관리할 수 있는 **개인 맞춤형 향수 아카이빙 앱**입니다. 서버의 메인 데이터베이스로 **Notion API**를 활용하며, 실시간 날씨 데이터와 향수 데이터를 결합하여 개인의 향수 취향을 완벽하게 분석합니다.

---
## 📱 스크린샷 (Screenshots)

| 메인 화면 & NFC 스캔 | 향수 옷장 (Season Bar) | 데이터 분석 (Note DNA) | 위시리스트 (노트 매칭) |
| :---: | :---: | :---: | :---: |
| <img src="여기에_메인화면_이미지링크.png" width="220" /> | <img src="여기에_옷장_이미지링크.png" width="220" /> | <img src="여기에_통계_이미지링크.png" width="220" /> | <img src="여기에_위시_이미지링크.png" width="220" /> |

*(※ 위 이미지는 실제 모바일 환경에서 구동되는 화면입니다.)*

## ✨ 핵심 기능 (Key Features)

### 1. ⚡ NFC 기반 1초 착향 로깅 (Zero-click Logging)
* 아이폰/안드로이드 스마트폰을 향수병에 부착된 NFC 태그에 대는 즉시 앱이 실행되며 착향 기록이 완료됩니다.
* 기록 시점의 **GPS 기반 실시간 날씨(온도, 습도, 날씨 상태)** 가 자동으로 함께 저장됩니다.

### 2. 🕷️ 향수 데이터 자동 스크래핑 (Automated Data Extraction)
* 웹 URL만 입력하면 `Playwright` 기반의 스크래핑 엔진이 작동하여, 해당 향수의 **향조(Top, Middle, Base Notes)**와 **대중의 투표 기반 계절 비중(Season Stats)** 데이터를 자동으로 추출하고 적재합니다.

### 3. ☁️ 노션(Notion) DB 연동 (Notion as a Database)
* 별도의 RDBMS 없이 **Notion을 백엔드 DB로 사용**합니다.
* 마스터 옷장(Wardrobe), 착향 로그(Usage Logs), 위시리스트(Wishlist), 사용자(Users) 등 모든 데이터가 노션 데이터베이스에 동기화 및 적재되어 직관적인 데이터 관리가 가능합니다.

### 4. 📊 스마트 대시보드 & 취향 분석 (Note DNA)
* **Heatmap**: 깃허브 잔디 심기 형태의 월별/일별 착향 빈도 시각화
* **Weather Pick**: 맑은 날, 흐린 날, 비 오는 날 등 날씨별 가장 많이 찾은 향수 통계
* **Note DNA**: 기후(Sensory Climate) 구간별 최애 노트(Golden)와 기피 노트(Warning)를 분석하는 레이더 차트 제공

### 5. 🧥 옷장 & 위시리스트 매칭 (Wardrobe Matching)
* 위시리스트에 향수를 등록하면, 현재 내 옷장(Wardrobe)에 있는 향수들의 노트와 비교하여 **향조 일치율(%)** 을 계산하고 비슷한 계열인지 새로운 계열인지 분석해 줍니다.

---

## 🛠️ 기술 스택 (Tech Stack)

### Backend
* **Framework**: Java 17, Spring Boot (WebFlux 연동)
* **Scraping**: Playwright (Headless Chrome + Xvfb)
* **Authentication**: JWT (JSON Web Token), jBCrypt
* **API Integration**: Notion API (DB 연동), OpenWeatherMap API (실시간 날씨/예보)

### Frontend
* **UI/UX**: Vanilla JavaScript, HTML5, CSS3 (Mobile-first 디자인)
* **PWA**: Manifest & Service Worker 지원 (모바일 앱처럼 홈 화면 추가 가능)
* **Libraries**: Chart.js (레이더/도넛 차트), SortableJS (드래그 앤 드롭 정렬)

### Infrastructure
* Docker & Docker Compose
* Cloudflare Tunnels (보안 터널링)

---

## ⚙️ 시스템 아키텍처 및 데이터 흐름

1. **Client (PWA)**: NFC 스캔 이벤트 또는 UI 인터랙션 발생
2. **Spring Boot Backend**: 비동기(WebFlux) 방식으로 트래픽 처리 및 JWT 인증 확인
3. **External API Calls**:
   * 향수 등록 시 `Playwright` 엔진이 글로벌 향수 웹 데이터베이스를 스크래핑.
   * 로그 기록 시 `OpenWeatherMap`에서 현재 좌표 기반 온습도 조회.
4. **Notion Database**: 모든 결과를 가공하여 Notion API(`POST /pages`, `PATCH /pages`)를 통해 노션 DB에 Insert/Update.

---

## 🚀 향후 로드맵 (Roadmap)

버전 1.0 릴리즈 이후 추가될 예정인 기능들입니다.

- [ ] **SSO (Social Login)**: 카카오/구글 로그인을 통한 계정 관리 편의성 증대
- [ ] **Push Notifications**: 외출 시간(아침)에 맞춘 착향 리마인더 및 추천 푸시 알림
- [ ] **PWA Offline Caching**: 네트워크 음영 구역에서도 오프라인 로깅 후 인터넷 연결 시 백그라운드 동기화 지원
- [ ] **Social Sharing**: 내 옷장 상태나 이달의 향수(Perfume of the Month) 이미지를 인스타그램 스토리에 공유하는 기능

---

## 👨‍💻 개발자 (Developer)
* **개발 및 기획**: [조승현/jshjshjshjsh]
* 본 프로젝트는 개인의 향수 수집 및 기록을 위해 개발된 1인 토이 프로젝트입니다.
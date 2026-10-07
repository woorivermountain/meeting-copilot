---
name: 모이다
description: 차콜과 흰 문서 중심의 정돈된 회의 작업 공간
colors:
  primary: "#292c33"
  primary-hover: "#14161b"
  ink: "#20242c"
  muted: "#606875"
  workspace: "#f6f7f9"
  white: "#fff"
  line: "#e3e6ec"
  accent-soft: "#f0f1f4"
  tab-selected: "#f0f1f3"
  link: "#355cc6"
  danger: "#b9353d"
  focus: "#6485ee"
typography:
  headline:
    fontFamily: "Pretendard, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "28px"
    fontWeight: 700
    lineHeight: 1.4
    letterSpacing: "-0.025em"
  title:
    fontFamily: "Pretendard, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "18px"
    fontWeight: 650
    lineHeight: 1.5
    letterSpacing: "-0.015em"
  body:
    fontFamily: "Pretendard, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.7
  transcript:
    fontFamily: "Pretendard, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.8
  label:
    fontFamily: "Pretendard, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "14px"
    fontWeight: 550
    lineHeight: 1.6
rounded:
  tag: "5px"
  tab: "6px"
  field: "7px"
  control: "8px"
  container: "12px"
  dialog: "16px"
spacing:
  compact: "8px"
  control: "12px"
  regular: "16px"
  group: "20px"
  section: "24px"
  document: "28px"
  page: "48px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.white}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
  button-secondary:
    backgroundColor: "{colors.white}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  button-quiet:
    backgroundColor: "transparent"
    textColor: "{colors.muted}"
    rounded: "{rounded.control}"
    padding: "7px 9px"
  button-danger:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.white}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  input:
    backgroundColor: "{colors.white}"
    textColor: "{colors.ink}"
    rounded: "{rounded.field}"
    padding: "11px 12px"
  document-tab:
    backgroundColor: "transparent"
    textColor: "{colors.muted}"
    rounded: "{rounded.tab}"
    padding: "9px 12px"
  document-tab-selected:
    backgroundColor: "{colors.tab-selected}"
    textColor: "{colors.ink}"
    rounded: "{rounded.tab}"
  status-badge:
    backgroundColor: "#f0f2f5"
    textColor: "{colors.muted}"
    rounded: "{rounded.tag}"
    padding: "4px 8px"
  panel:
    backgroundColor: "{colors.white}"
    textColor: "{colors.ink}"
    rounded: "{rounded.container}"
    padding: "24px"
---

# Design System: 모이다

## Overview

**Creative North Star: "정돈된 회의 문서"**

모이다는 정돈된 문서 작업 공간입니다. 사용자가 선택한 Notion식 문서 중심 방향을 따라 흰 바탕, 차콜 액션, 짧은 한국어와 명확한 구획으로 기록을 읽고 정리하는 데 집중합니다.

표면은 평평하고 조용합니다. 제목과 본문이 위계를 만들고, 경계선과 간격이 영역을 구분합니다. 회의의 상태와 사용자 행동에 필요한 정보만 강조하며 장식적인 대시보드나 통화 무대처럼 보이지 않습니다.

**Key Characteristics:**

- 흰 문서와 차콜 중심의 조용한 위계
- 자체 제공 Pretendard와 짧은 한국어
- 간격과 가는 구분선 중심의 평평한 표면
- 상태를 드러내되 실제 데이터 이상을 주장하지 않는 UI

이 문서는 현재 Vue 컴포넌트와 `apps/web/src/styles.css`의 최종 덮어쓰기까지 반영한 구현 기록입니다. 초기 파란 기본 버튼과 폐기된 하단 통화 도크는 현행 회의 디자인의 기준이 아닙니다. 회의록과 AI 도우미의 독립 스크롤만 예외적으로 좌우 배치를 사용합니다.

## Colors

차콜과 차가운 회색이 기본이며 파랑은 보조 링크와 일부 안내·상태에 남아 있습니다. 위 frontmatter가 색 값의 기준입니다.

### Primary

- **차콜** (`primary`): 기본 액션과 브랜드 마크. 마우스를 올리면 더 짙은 차콜로 바뀝니다.
- **옅은 선택면** (`accent-soft`, `tab-selected`): 눌린 컨트롤과 현재 문서 섹션을 드러냅니다.

### Secondary

- **조용한 파랑** (`link`): 로그인 전환과 직접 입력 같은 보조 행동. 기존 사이드바, 배지, 포커스에는 별도의 청색 계열이 쓰입니다.
- **경고 빨강** (`danger`): 회의 종료·저장하지 않고 나가기 같은 주의가 필요한 액션. 일반 강조색이 아닙니다.

### Neutral

- **본문 잉크** (`ink`): 제목, 본문, 보조 버튼.
- **설명 회색** (`muted`): 보조 설명, 시각, 비선택 탭.
- **작업 공간 회색** (`workspace`): 팀·목록 화면의 외곽 바탕.
- **문서 흰색** (`white`): 회의 문서, 인증 화면, 입력, 컨테이너.
- **구분선 회색** (`line`): 목록 행과 작업 영역의 경계.

**The Charcoal Action Rule.** 기본 행동은 차콜로 표시하고, 파랑을 기본 버튼색으로 되돌리지 않습니다.

## Typography

모든 역할은 자체 제공 Pretendard Variable을 사용하며 시스템 산세리프로 대체합니다. 장식적인 디스플레이 서체나 별도의 대문자 라벨 체계는 없습니다.

- **Headline:** 회의 제목. 일반 페이지 제목은 같은 크기에 줄 높이 (1.35), 회의 제목은 frontmatter의 (1.4)를 사용합니다. 모바일 회의 제목은 (22px), 일반 페이지 제목은 (25px)입니다.
- **Title:** 문서 섹션 제목. 작은 하위 제목은 (17px), 굵기 (650)입니다.
- **Body:** 기본 크기 (15px), 문단 줄 높이 (1.7). 안내 문단에는 (14px), 도움말에는 (13px)가 쓰입니다.
- **Transcript:** 대화 기록은 줄 높이 (1.8), 최대 줄 길이 (75ch)입니다. 모바일 본문은 (15px)로 줄어듭니다.
- **Label:** 입력 이름은 굵기 (550), 버튼은 (600), 선택 문서 탭은 (650)입니다. 시각과 상태 메타데이터는 (12px)입니다.

**The Action Copy Rule.** 사용자에게 필요한 행동과 상태를 짧은 한국어로 씁니다.

## Layout

서비스 셸은 데스크톱에서 헤더 (60px), 사이드바 (208px), 유동 본문으로 구성됩니다. 로그인과 회의에서는 사이드바를 제거합니다. 일반 작업 공간은 최대 (1100px), 로그인 폼은 최대 (380px)입니다.

현재 회의 표면은 기본적으로 최대 (1040px)의 중앙 문서이며 제목 아래 도구줄은 최대 (928px)입니다. 도구줄 왼쪽에는 대화 기록·요약·결정·할 일 선택, 오른쪽에는 전사와 저장 액션을 둡니다. 요약·남길 내용·기록 저장은 한 번에 하나만 보이며 탭 전환은 경로 이동이 아닙니다. 숨겨진 전사 컴포넌트는 마운트 상태를 유지합니다. 하단 통화 도크는 없습니다.

AI 도우미를 열면 데스크톱 작업 폭은 최대 (1320px)로 넓어지고 회의록 옆에 (410px) 보조 패널이 나타납니다. 두 영역은 각각 스크롤하며 AI 답변 도착이 사용자가 읽는 회의록 위치를 바꾸지 않습니다. 폭 (900px) 이하에서는 AI가 전체 내용 영역을 사용하고 닫으면 같은 위치의 회의록으로 돌아갑니다. 이 좌우 배치는 지속해서 참고하는 대화와 일시적으로 묻는 보조 작업 사이의 관계를 위한 유일한 예외입니다.

회의 컨테이너는 헤더를 제외한 뷰포트 높이를 사용합니다. 상단 제목과 도구줄은 스크롤되는 내용 영역 밖에 남습니다. 현재 구현은 `position: sticky`가 아니라 flex 셸과 내부 스크롤입니다. 기록 영역은 데스크톱에서 좌우 (28px)의 안쪽 여백을 갖고, 추가 도구 폼은 최대 (520px), 섹션은 최대 (760px)입니다. 전사는 원문 ID를 보존한 표시 전용 문단 묶음으로 읽기 흐름을 만들며, 사용자가 최신 부분 근처에 있을 때만 새 문장을 따라갑니다.

폭 (1100px) 이하에서는 일반 페이지 여백이 (32px)로 줄어듭니다. 폭 (760px) 이하에서는 헤더가 (56px)가 되고 사이드바를 숨깁니다. 문서 도구줄은 섹션 선택과 액션의 두 줄로 나뉘며, 문서 바깥 여백은 (18px), 내부 기록 좌우 여백은 없어집니다. 모바일 회의 액션은 최소 높이 (38px)입니다.

## Elevation & Depth

기본 화면은 그림자 없이 흰 면, 옅은 회색 바탕, 가는 경계선으로 구분합니다. 회의 본문과 도구 섹션은 카드처럼 둘러싸지 않습니다. 모달만 명시적인 그림자와 어두운 배경막을 사용합니다.

- **대화상자 그림자:** `0 24px 80px #18243c33`.
- **대화상자 배경막:** `#111a2d66`.
- **키보드 포커스:** `3px solid #6485ee`, 바깥 간격 (3px).

**The Flat Document Rule.** 문서의 위계는 카드 그림자가 아니라 제목, 간격, 구분선으로 만듭니다.

## Shapes

입력은 작게 둥근 모서리, 버튼은 부드러운 직사각형, 문서 선택은 더 얕은 곡률입니다. 목록과 일반 폼 컨테이너는 (12px), 대화상자는 (16px)를 사용합니다. 회의 본문과 도구 영역은 모서리 장식이 없는 평평한 문서입니다. 아바타와 상태 점에만 원형을 사용합니다.

## Components

### Buttons

간결한 행동 중심입니다. 기본 버튼은 최소 높이 (42px), 굵기 (600), 줄 높이 (1.35)이며 차콜과 흰 글자를 사용합니다. 문서 도구줄은 데스크톱에서 높이 (36px), 글자 (13px), 패딩 (8px 11px)로 압축됩니다.

보조 버튼은 흰 바탕과 옅은 테두리, 조용한 버튼은 투명 바탕과 회색 글자입니다. 위험 버튼은 빨강입니다. 비활성은 불투명도 (0.48)와 사용 불가 커서를 사용합니다. 모든 키보드 포커스는 공통 링을 유지합니다. 상태 전환에 별도의 애니메이션은 없습니다.

### Inputs / Fields

흰 바탕, 얇은 회색 테두리, 안쪽 여백과 영구 라벨을 사용합니다. 입력과 텍스트 영역의 줄 높이는 (1.55)입니다. 입력 오류는 붉은 안내 문단으로 표시되며 별도의 필드별 오류 테두리를 구현한 것으로 가정하지 않습니다.

### Navigation

문서 선택은 둥근 사각형 버튼 묶음이며 `aria-pressed`로 선택을 표시합니다. 선택면은 옅은 회색, 글자는 본문 잉크입니다. 실제 구현은 ARIA tablist가 아닌 버튼 navigation입니다. 팀 작업 공간의 회의·자료함 이동은 밑줄 선택 방식이며, 사이드바는 별도의 옅은 파란 선택면을 사용합니다.

### Chips / Status

회의 목록 상태 배지는 조용한 사각 배지입니다. 진행 가능 배지는 옅은 파랑이며, 종료 배지는 회색입니다. 회의 중 전사 상태는 짧은 문구와 작은 점으로 표시합니다. 색만으로 상태를 전달하지 않습니다.

앱 헤더의 개발 버전은 브랜드 이름 옆에 작은 사각 상태 배지로 표시합니다. 현재 표기는 `v0.3.0-alpha.1 · 1차 테스트 반영본`이며 정식 출시로 오인되지 않게 합니다. 모바일에서는 버전 번호만 남겨 탐색 행동을 가리지 않습니다.

### Cards / Containers

일반 자료 패널, 생성 폼과 목록은 흰 면과 얇은 경계선으로 정리합니다. 일반 패널의 내부 여백은 (24px), 생성 폼은 (26px)입니다. 목록은 개별 떠 있는 카드가 아닌 구분선 있는 행 묶음입니다.

### Document Content

대화 기록은 수신 시각, 문장, 수정 행동으로 구성됩니다. 빈 상태는 왼쪽 정렬의 짧은 안내입니다. `최신 대화 따라가기`는 대화 영역 안에 고정되며, 사용자가 이전 내용으로 스크롤하면 꺼지고 놓친 문장 수를 보여줍니다. 요약과 할 일은 같은 문서 폭 안에서 나타나며, 모바일에서도 별도의 통화 화면으로 바뀌지 않습니다. AI 답변은 회의 원문에 근거한 내용, 일반 전문 지식, 확인할 가정을 명시적으로 나눕니다. 표시 예시나 문서 미리보기는 실제 회의 기록으로 오인되지 않아야 합니다.

### Usage Ledger

AI 사용량은 큰 숫자 카드 대신 구분선이 있는 수치 행, 기능별 표, 최근 요청 원장으로 표시합니다. 공급자 실측과 문자 수 추정을 항상 구분하며 비용을 추정하지 않습니다. 프롬프트·회의 원문·답변은 사용량 화면이나 사용량 이벤트에 포함하지 않습니다.

### Dialogs

기본 HTML dialog를 사용합니다. 최대 폭 (440px), 패딩 (28px), 모바일 패딩 (22px)입니다. 닫기·취소와 주요 행동을 구분하고 이전 포커스를 복원합니다. 이탈과 종료는 서로 다른 확인 화면입니다. 시스템의 동작 줄이기 설정에서는 애니메이션과 전환을 제거합니다.

## Do's and Don'ts

### Do:

- Do use 차콜 기본 액션과 흰 문서면을 유지합니다.
- Do use 짧고 행동 중심적인 한국어를 사용합니다.
- Do keep 키보드 포커스와 텍스트 상태 설명을 유지합니다.
- Do keep 저장 전·저장됨·오류 상태를 실제 데이터에 맞게 표현합니다.

### Don't:

- Don't restore 따뜻한 베이지 테마나 장식적인 AI 설명을 되돌리지 않습니다.
- Don't turn 현재 회의 문서를 하단 통화 도크로 바꾸거나 AI 이외의 여러 도구를 동시에 쌓지 않습니다.
- Don't imply 자동 저장, 자동 화자 구분, 화상 회의 또는 실시간 공동 편집을 암시하지 않습니다.

# devpath-community-svc

**DevPath AI** 커뮤니티 서비스 — 게시판, 평판, 배지, 모더레이션을 담당합니다.

## 담당 도메인

| 모듈 | 역할 |
|------|------|
| post | 5개 게시판 (Q&A / 자유 / 프로젝트 / 스터디 / 수료자 라운지) |
| reputation | 평판 이벤트 엔진 + 태그별 평판 + sockpuppet 탐지 |
| badge | Bronze/Silver/Gold 자동 수여 |
| moderation | AI 모더레이션 + 신고 + 제재 + 이의제기 |
| learning-context | 학습 맥락 스냅샷 수집/렌더 |
| ai-seed | AI 시드 답변 파이프라인 (Claude) |

설계 문서: [documents/20_커뮤니티_기능_설계서](https://github.com/DevPathAi/documents/blob/main/20_커뮤니티_기능_설계서.md)

## 구성

- Spring Boot 4.0.x · Java 21 · Gradle (Kotlin DSL)
- [devpath-svc-template](https://github.com/DevPathAi/devpath-svc-template) 기반
- DB 의존성(JPA + MySQL, Redis)은 `build.gradle.kts` 주석 해제로 활성화

## 빌드 / 실행

```bash
./gradlew build
./gradlew bootRun    # 기본 포트 8080
```

로컬 인프라는 [devpath-shared](https://github.com/DevPathAi/devpath-shared)의 docker-compose를 사용합니다.

## 개발 규칙

- Git 규칙: [documents/09_Git_규칙_정의서](https://github.com/DevPathAi/documents/blob/main/09_Git_규칙_정의서.md)
- 워크플로우 현황: `docs/project-management/` → [workflow-dashboard](https://devpathai.github.io/workflow-dashboard/)

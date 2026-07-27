## Step 1: #8 커뮤니티 Q&A
### 1.1 Q&A
- [ ] Q&A CRUD(목록/상세/작성/답변/채택) + 태그 자동완성 + ES 검색 인덱스
- [ ] AI 시드 답변 Worker(질문 즉시 Claude→community_ai_answers) + 유사 질문 탐지(pgvector 0.80)
- [ ] 자유게시판 + 프로젝트 공유 게시판
## Step 2: #9 LCS (moat)
### 2.1 스냅샷·Sanitize
- [ ] learning_context_snapshots 자동 수집(질문 작성 시 Opt-in 토글)
- [ ] 에러 로그 민감정보 3단계 Sanitize 파이프라인(API 키·이메일·토큰 마스킹)
- [ ] 답변자 UI 맥락 패널(학습 경로·현재 콘텐츠·최근 에러) + 개별 on/off·미리보기·공개범위
## Step 3: 평판 기초
### 3.1 평판·스트릭
- [x] 평판 엔진(upvote/downvote/채택) + 태그별 평판(user_tag_reputation) — **Build 1 완료**(2026-06-30, PR #13 / shared 스키마 #30). 가산 +5/+10·채택 +15/+2·downvote −2/−1·투표변경 역산
- [x] 레벨별 권한(15/125/500/1000) + Bronze 배지 9종 + 일일 +40 상한·sockpuppet 탐지 — **완료(Build 1+2+3)**: 게이트 15/125 ✅·일일 +40 상한 ✅(B1) · **Bronze 배지 엔진 ✅**(B2, 2026-06-30, PR #15 / shared 스키마 #31) — 9종 시드, 신호 있는 6종 트리거 결선(FIRST_QUESTION·FIRST_ANSWER·STUDENT·TEACHER·CRITIC·PHILANTHROPIST) + 멱등 수여 + `community.badge.awarded` outbox + 조회 API · **자기 글 투표 금지 + 행동 기반 담합 탐지 ✅**(B3, 2026-07-01, PR #17 / shared 스키마 #32) — votePost/voteAnswer 레벨게이트 앞 하드 차단(upvote·downvote 모두) + `CollusionDetector`(reputation_events 기반, DISTINCT upvote≥5 시 `vote_abuse_suspicions` 멱등 기록 + `community.reputation.suspected` outbox, 기록만·회수 없음). **후속**: 게이트 500/1000, 배지 3종 dormant(FIRST_STEP·EDITOR·COMMUNITY 시드만, 상위 기능 도입 시 결선)·Silver/Gold, IP/디바이스 sockpuppet·신계정 7일 제한(platform-svc)·moderation 자동 제재(suspicion 이벤트 소비)
- [ ] 스트릭(TZ)·주간 리포트 배치·3일 미접속 AI 제안·선호 시간대 푸시

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
- [ ] 평판 엔진(upvote/downvote/채택) + 태그별 평판(user_tag_reputation)
- [ ] 레벨별 권한(15/125/500/1000) + Bronze 배지 9종 + 일일 +40 상한·sockpuppet 탐지
- [ ] 스트릭(TZ)·주간 리포트 배치·3일 미접속 AI 제안·선호 시간대 푸시

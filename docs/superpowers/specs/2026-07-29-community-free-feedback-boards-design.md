# 커뮤니티 FREE/FEEDBACK 보드 + 댓글 (백엔드) 설계

- 날짜: 2026-07-29
- 상태: **사용자 승인**(구현 전 — 이 spec 리뷰 후 플랜 작성)
- 대상 레포: `devpath-shared`(마이그레이션) · `devpath-community-svc`(백엔드)
- 우선순위: 로컬 4이슈 워크스트림 **②** 두 번째 부분(멘토 완료 후). **하위 프로젝트 A(백엔드)** — 프론트 화면(web)은 별도 하위 프로젝트 B.

## 1. 배경 (코드 실측)

사용자 피드백: "커뮤니티 자유(FREE)·질의응답·피드백." Q&A는 구현됨. FREE/FEEDBACK은 미구현이다.

- `community_posts`(shared `V202606251001__community_qna.sql`): `id, author_id, board_type VARCHAR(16), title(120), body_md, body_html, status, view/upvote/downvote_count, timestamps`. **board_type 체크 = `QNA,FREE,PROJECT,STUDY,ALUMNI`**(FEEDBACK 없음). status 체크 = `DRAFT,PUBLISHED,HIDDEN,DELETED`.
- `community_questions`(post_id PK→posts, is_solved, accepted_answer_id, ...), `community_answers`(question_id→questions, ...) — **Q&A 전용**.
- `CommunityController /community`: `POST /questions`(Q&A 생성, boardType="QNA" 하드코딩), `POST /questions/{id}/answers`, `POST /answers/{id}/accept`, `GET /questions/{id}`(Q&A 상세, CommunityQuestion 필요), `GET /posts?board=&tag=&sort=`(보드별 목록, 범용), `POST /posts/{id}/vote`, `GET /tags`.
- `QuestionService.create()`가 boardType="QNA" 하드코딩 + `CommunityQuestion` 생성 + FIRST_QUESTION 배지 + `CommunityQuestionPostedEvent`(Outbox) 발행 → **일반 게시글에 부적합**. `list(board)`=`CommunityPostRepository.findBoardNewest(board)`(PUBLISHED, 최신순)만 범용.
- **댓글 엔티티 없음**(Q&A는 answers만).
- **community-svc는 shared를 Maven 의존성**(`ai.devpath:devpath-shared:0.0.1-SNAPSHOT`, GitHub Packages)으로 소비하고 **자체 db/migration 디렉토리 없음** — 마이그레이션은 shared JAR에 번들. 따라서 신규 마이그는 shared에 추가→발행→community-svc가 소비.
- 설계서 `documents/20_커뮤니티_기능_설계서.md`: ②자유게시판(일반 토론, 일반 댓글 평판 영향 없음, 인기글 좋아요50+ +20 보너스), ④피드백(코드/프로젝트 피드백).

## 2. 목표 / 비목표

**목표**
- FREE(자유 토론)·FEEDBACK(코드/프로젝트 피드백) 보드의 **일반 게시글 작성/목록/상세 + 댓글**을 백엔드 API로 제공한다.
- Q&A 경로(질문/답변/채택)와 **분리**해 일반 게시글에 Q&A 의미(solved/accept)가 새지 않게 한다.
- 절대조건 준수: 추측 금지·Test-First·자화자찬 금지·작업 브랜치.

**비목표 (후속/별도)**
- **프론트 화면(web)** — 하위 프로젝트 B.
- **평판·배지**: 일반 게시글/댓글은 평판·배지 이벤트 미발행(설계서: 자유 댓글 평판 영향 없음). 인기글 +20 보너스는 후속.
- 댓글 추천(vote), 게시글 수정/삭제, 신고/모더레이션 — 후속. 게시글 추천은 기존 `POST /posts/{id}/vote` 재사용.
- STUDY/ALUMNI/PROJECT 보드 — 범위 밖.

## 3. 설계

### 3.1 shared 마이그레이션 (`V202607291001__community_feedback_and_comments.sql`)
- **board_type 체크 제약 교체**: 기존 `chk_community_posts_board` DROP 후 `QNA,FREE,PROJECT,STUDY,ALUMNI,FEEDBACK` 로 재생성.
- **`community_comments` 신규**:
  ```sql
  CREATE TABLE community_comments (
    id           BIGSERIAL PRIMARY KEY,
    post_id      BIGINT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    author_id    BIGINT NOT NULL,
    body_md      TEXT NOT NULL,
    body_html    TEXT,
    upvote_count INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  CREATE INDEX idx_community_comments_post ON community_comments(post_id, created_at);
  CREATE TRIGGER community_comments_set_updated_at BEFORE UPDATE ON community_comments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
  ```
- **발행 의존성**: shared develop 머지 후 SNAPSHOT 발행(`gh workflow run publish.yml --ref develop` 수동 — main push만 자동). community-svc는 발행본을 소비. **로컬 검증**은 shared를 `publishToMavenLocal`(또는 로컬 빌드)로 community-svc가 픽업하게 하거나, 로컬 DB에 마이그레이션을 직접 적용해 테스트.

### 3.2 community-svc 엔티티/리포지토리
- `CommunityComment`(엔티티, `community_comments` 매핑): id, postId, authorId, bodyMd, bodyHtml, upvoteCount, createdAt, updatedAt(기존 `CommunityPost` 스타일 — insertable=false 타임스탬프).
- `CommunityCommentRepository extends JpaRepository<CommunityComment, Long>`: `List<CommunityComment> findByPostIdOrderByCreatedAtAsc(long postId)`, `long countByPostId(long postId)`.

### 3.3 community-svc 서비스
- **`PostService`(신규)** — Q&A와 분리된 일반 게시글:
  - `PostDetailView createPost(long userId, CreatePostRequest req)`: `board = req.boardType()`가 **FREE 또는 FEEDBACK만 허용**(그 외 `IllegalArgumentException`). `CommunityPost`(boardType, title, bodyMd, status=PUBLISHED, authorId) 저장. 태그는 요청에 있으면 기존 `CommunityTag`/`CommunityPostTag` 로직 재사용(QuestionService와 동일 방식). **CommunityQuestion 생성 안 함, 이벤트/배지 발행 안 함.** `postDetail(savedId)` 반환.
  - `PostDetailView postDetail(long postId)`: `CommunityPost` 조회(없으면 `NotFoundException`). 해당 post가 Q&A(QNA)면 이 경로에서 거부하지 않되(범용 조회 허용) 댓글 목록을 실어 반환. 태그·댓글 목록 포함.
  - 목록은 기존 `QuestionService.list(board,tag,sort)` 또는 `CommunityPostRepository.findBoardNewest`를 재사용(신규 작성 불필요).
- **`CommentService`(신규)**:
  - `CommentView addComment(long userId, long postId, CreateCommentRequest req)`: post 존재 확인(없으면 `NotFoundException`), `CommunityComment` 저장, `CommentView` 반환. **평판/배지/이벤트 발행 없음.**
  - `List<CommentView> listComments(long postId)`.

### 3.4 community-svc 컨트롤러 (`CommunityController` 확장)
- `POST /community/posts` → `postService.createPost(uid, CreatePostRequest)` → 201 `PostDetailView`.
  - 주의: 현재 `GET /community/posts`(목록)만 있음 → `POST` 메서드 추가(경로 동일, 메서드 분리).
- `GET /community/posts/{id}` → `postService.postDetail(id)` → `PostDetailView`.
  - 주의: 기존 `GET /community/questions/{id}`(Q&A 상세)와 구분되는 일반 상세.
- `POST /community/posts/{id}/comments` → `commentService.addComment(uid, id, CreateCommentRequest)` → 201 `CommentView`.
- `GET /community/posts/{id}/comments` → `commentService.listComments(id)`(또는 상세에 포함하고 생략 — 상세 포함 + 별도 목록 둘 다 제공).
- 게시글 추천은 기존 `POST /community/posts/{id}/vote` 재사용(코드 변경 없음).

### 3.5 DTO (신규, `post/dto`)
- `CreatePostRequest(String boardType, String title, String bodyMd, java.util.List<String> tags)`.
- `PostDetailView(long id, String boardType, String title, String bodyMd, Long authorId, int upvoteCount, int downvoteCount, java.util.List<String> tags, java.util.List<CommentView> comments)`.
- `CommentView(long id, Long authorId, String bodyMd, int upvoteCount, java.time.Instant createdAt)`.
- `CreateCommentRequest(String bodyMd)`.

## 4. 테스트 (Test-First)

- `PostServiceTest`(단위/슬라이스): FREE 생성→boardType FREE·CommunityQuestion 미생성·이벤트 미발행 확인 / FEEDBACK 생성 성공 / QNA·기타 boardType 거부(`IllegalArgumentException`) / postDetail 댓글 포함 / 없는 post 상세 → NotFound.
- `CommentServiceTest`(단위/슬라이스): addComment→저장·CommentView 반환·평판 이벤트 미발행 / listComments 시간순 / 없는 post 댓글 → NotFound.
- `CommunityCommentRepository` 슬라이스: findByPostIdOrderByCreatedAtAsc·countByPostId.
- 컨트롤러 슬라이스(`@WebMvcTest` 또는 기존 통합 스타일): `POST /community/posts`(201), `GET /community/posts/{id}`, `POST /community/posts/{id}/comments`(201).
- 마이그레이션 검증: FEEDBACK enum 허용·community_comments 적재(로컬 DB 또는 Flyway 테스트).
- 기존 Q&A 테스트 green 유지.

## 5. 리스크

- **shared 발행 의존성**: community-svc가 새 마이그를 받으려면 shared SNAPSHOT 발행 필요. 로컬은 `publishToMavenLocal` 또는 DB 직접 마이그 적용으로 우회. 순서=shared 먼저.
- **경로 충돌**: `GET /community/posts`(목록) vs `POST /community/posts`(생성) — HTTP 메서드로 구분(문제 없음). `GET /community/posts/{id}`(일반 상세) vs `GET /community/questions/{id}`(Q&A 상세) — 경로 분리.
- **QNA 오작성 방지**: createPost가 QNA를 거부해 일반 경로로 Q&A가 새지 않게.

## 6. 순서 / 롤아웃

1. **shared**: 마이그레이션 추가 → 브랜치 PR → (로컬 검증은 publishToMavenLocal/DB 직접적용) → develop 머지 → SNAPSHOT 발행.
2. **community-svc**: 엔티티/리포/서비스/DTO/컨트롤러 + 테스트 → develop PR.
3. (후속) 하위 프로젝트 B: 프론트 web 보드 화면.

## 7. 영향 범위

| 레포 | 변경 |
|---|---|
| devpath-shared | `V202607291001__community_feedback_and_comments.sql`(FEEDBACK enum + community_comments) · 발행 |
| devpath-community-svc | `post/CommunityComment`·`CommunityCommentRepository`·`PostService`·`CommentService`·`post/dto/*`(4)·`CommunityController` 확장 · 테스트 |
| devpath-frontend | (후속 B) web 보드 화면 |

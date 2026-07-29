# 커뮤니티 FREE/FEEDBACK 보드 + 댓글 (백엔드) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** FREE(자유)·FEEDBACK(피드백) 보드의 일반 게시글 작성/목록/상세 + 댓글을 Q&A 경로와 분리해 백엔드 API로 제공한다.

**Architecture:** shared에 마이그레이션(FEEDBACK enum + community_comments) 추가, community-svc에 Q&A와 분리된 `PostService`/`CommentService` + 컨트롤러 엔드포인트 추가. 게시글/댓글은 평판·배지·이벤트를 발행하지 않는다(MVP).

**Tech Stack:** Java 21, Spring Boot 4.0.7, Gradle(Kotlin DSL), JUnit 5, MockMvc, Flyway, PostgreSQL(pgvector), 실 DB 테스트(`devpath_citest`).

## Global Constraints

- 두 레포: `devpath-shared`(마이그, 절대경로 `D:\workspace\dpa\devpath-shared`) · `devpath-community-svc`(백엔드, `D:\workspace\dpa\devpath-community-svc`). **모든 git/파일 명령은 절대경로 또는 `-C <repo>` 사용**(cwd 리셋 주의).
- community-svc는 shared를 Maven 의존성(`ai.devpath:devpath-shared:0.0.1-SNAPSHOT`)으로 소비. 자체 db/migration 없음. 마이그는 shared JAR 번들.
- **로컬 소비 스캐폴딩(미커밋)**: ① shared `./gradlew publishToMavenLocal`. ② community-svc `build.gradle.kts` repositories 최상단에 `mavenLocal()` 임시 추가(테스트 후 되돌림, 커밋 금지). ③ 로컬 DB 생성: `docker exec devpath-local-postgres-1 psql -U devpath -d postgres -c "CREATE DATABASE devpath_citest OWNER devpath;"`.
- 테스트 스타일: `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`, 인증 `.with(jwt().jwt(j -> j.subject("<userId>")))`, JsonPath 검증. 빌드/테스트 `./gradlew build`·`test`.
- 패키지 `ai.devpath.community.post`. DTO 패키지 `ai.devpath.community.post.dto`. 예외 `ai.devpath.community.post.NotFoundException`(RESOURCE_NOT_FOUND 404).
- 게시글/댓글은 **평판·배지·Outbox 이벤트 미발행**. boardType은 **FREE/FEEDBACK만** createPost 허용.
- 작업 브랜치: shared=`feat/community-comments-migration`(origin/develop 분기), community-svc=`feat/community-free-feedback-boards`(이미 생성). Conventional Commits. Test-First.

---

### Task 1: shared 마이그레이션 (FEEDBACK enum + community_comments)

**Files:**
- Create: `devpath-shared/src/main/resources/db/migration/V202607291001__community_feedback_and_comments.sql`

**Interfaces:**
- Produces: `community_comments` 테이블 + board_type 체크에 'FEEDBACK' 추가. community-svc가 소비.

- [ ] **Step 1: shared 작업 브랜치**

Run: `git -C D:/workspace/dpa/devpath-shared switch -c feat/community-comments-migration origin/develop`

- [ ] **Step 2: 마이그레이션 작성**

Create `devpath-shared/src/main/resources/db/migration/V202607291001__community_feedback_and_comments.sql`:

```sql
-- 커뮤니티 FREE/FEEDBACK 보드 + 댓글. board_type에 FEEDBACK 추가, 일반 게시글 댓글 테이블.
ALTER TABLE community_posts DROP CONSTRAINT chk_community_posts_board;
ALTER TABLE community_posts ADD CONSTRAINT chk_community_posts_board
  CHECK (board_type IN ('QNA','FREE','PROJECT','STUDY','ALUMNI','FEEDBACK'));

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

- [ ] **Step 3: shared 빌드 + 로컬 발행**

Run: `git -C D:/workspace/dpa/devpath-shared add src/main/resources/db/migration/V202607291001__community_feedback_and_comments.sql`
Run: `cd /d/workspace/dpa/devpath-shared && ./gradlew build publishToMavenLocal --offline`
Expected: BUILD SUCCESSFUL(마이그 SQL은 Flyway 검증 대상; shared 자체 테스트가 있으면 통과). 로컬 `~/.m2`에 `ai.devpath:devpath-shared:0.0.1-SNAPSHOT` 발행.

- [ ] **Step 4: 커밋**

```bash
git -C D:/workspace/dpa/devpath-shared commit -m "feat(db): 커뮤니티 FEEDBACK 보드 enum + community_comments 테이블"
```

---

### Task 2: CommunityComment 엔티티 + 리포지토리

**Files:**
- Create: `src/main/java/ai/devpath/community/post/CommunityComment.java`
- Create: `src/main/java/ai/devpath/community/post/CommunityCommentRepository.java`
- Test: `src/test/java/ai/devpath/community/post/CommunityCommentRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1 `community_comments`.
- Produces: `CommunityComment`(getId/getPostId/getAuthorId/getBodyMd/getBodyHtml/getUpvoteCount/getCreatedAt/getUpdatedAt + setters for postId/authorId/bodyMd/bodyHtml), `CommunityCommentRepository.findByPostIdOrderByCreatedAtAsc(long)`·`countByPostId(long)`.

- [ ] **Step 1: 로컬 스캐폴딩 준비(미커밋)**

Run: `docker exec devpath-local-postgres-1 psql -U devpath -d postgres -c "CREATE DATABASE devpath_citest OWNER devpath;"` (이미 있으면 무시).
`build.gradle.kts` repositories 블록 최상단에 `mavenLocal()` 한 줄 임시 추가(커밋 금지 — Task 4 완료 후 되돌림).

- [ ] **Step 2: 실패 테스트 작성**

`CommunityCommentRepositoryTest.java`:

```java
package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CommunityCommentRepositoryTest {

  @Autowired CommunityPostRepository posts;
  @Autowired CommunityCommentRepository comments;

  @Test
  void savesAndListsCommentsByPostInOrder() {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(1L); p.setBoardType("FREE"); p.setTitle("t"); p.setBodyMd("b"); p.setStatus("PUBLISHED");
    p = posts.save(p);

    CommunityComment c1 = new CommunityComment();
    c1.setPostId(p.getId()); c1.setAuthorId(2L); c1.setBodyMd("첫 댓글");
    comments.save(c1);
    CommunityComment c2 = new CommunityComment();
    c2.setPostId(p.getId()); c2.setAuthorId(3L); c2.setBodyMd("둘째 댓글");
    comments.save(c2);

    List<CommunityComment> list = comments.findByPostIdOrderByCreatedAtAsc(p.getId());
    assertThat(list).extracting(CommunityComment::getBodyMd).containsExactly("첫 댓글", "둘째 댓글");
    assertThat(comments.countByPostId(p.getId())).isEqualTo(2);
  }
}
```

- [ ] **Step 3: 실패 확인**

Run: `cd /d/workspace/dpa/devpath-community-svc && ./gradlew test --tests "ai.devpath.community.post.CommunityCommentRepositoryTest"`
Expected: FAIL(CommunityComment/CommunityCommentRepository 없음).

- [ ] **Step 4: 엔티티 구현**

`CommunityComment.java`:

```java
package ai.devpath.community.post;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_comments")
public class CommunityComment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "post_id", nullable = false) private Long postId;
  @Column(name = "author_id", nullable = false) private Long authorId;
  @Column(name = "body_md", nullable = false) private String bodyMd;
  @Column(name = "body_html") private String bodyHtml;
  @Column(name = "upvote_count", nullable = false) private int upvoteCount = 0;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
  @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;

  public Long getId() { return id; }
  public Long getPostId() { return postId; }
  public void setPostId(Long postId) { this.postId = postId; }
  public Long getAuthorId() { return authorId; }
  public void setAuthorId(Long authorId) { this.authorId = authorId; }
  public String getBodyMd() { return bodyMd; }
  public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
  public String getBodyHtml() { return bodyHtml; }
  public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
  public int getUpvoteCount() { return upvoteCount; }
  public void setUpvoteCount(int upvoteCount) { this.upvoteCount = upvoteCount; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
```

`CommunityCommentRepository.java`:

```java
package ai.devpath.community.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
  List<CommunityComment> findByPostIdOrderByCreatedAtAsc(long postId);
  long countByPostId(long postId);
}
```

- [ ] **Step 5: 통과 확인**

Run: `cd /d/workspace/dpa/devpath-community-svc && ./gradlew test --tests "ai.devpath.community.post.CommunityCommentRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: 커밋** (mavenLocal 스캐폴딩은 스테이징하지 말 것)

```bash
git -C D:/workspace/dpa/devpath-community-svc add \
  src/main/java/ai/devpath/community/post/CommunityComment.java \
  src/main/java/ai/devpath/community/post/CommunityCommentRepository.java \
  src/test/java/ai/devpath/community/post/CommunityCommentRepositoryTest.java
git -C D:/workspace/dpa/devpath-community-svc commit -m "feat(community): CommunityComment 엔티티 + 리포지토리"
```

---

### Task 3: DTO + PostService + CommentService

**Files:**
- Create: `src/main/java/ai/devpath/community/post/dto/CreatePostRequest.java`
- Create: `src/main/java/ai/devpath/community/post/dto/PostDetailView.java`
- Create: `src/main/java/ai/devpath/community/post/dto/CommentView.java`
- Create: `src/main/java/ai/devpath/community/post/dto/CreateCommentRequest.java`
- Create: `src/main/java/ai/devpath/community/post/PostService.java`
- Create: `src/main/java/ai/devpath/community/post/CommentService.java`
- Test: `src/test/java/ai/devpath/community/post/PostServiceTest.java`
- Test: `src/test/java/ai/devpath/community/post/CommentServiceTest.java`

**Interfaces:**
- Consumes: Task 2 엔티티/리포; 기존 `CommunityPostRepository`·`CommunityTagRepository`·`CommunityPostTagRepository`·`NotFoundException`.
- Produces:
  - `CreatePostRequest(String boardType, String title, String bodyMd, java.util.List<String> tags)`
  - `PostDetailView(long id, String boardType, String title, String bodyMd, Long authorId, int upvoteCount, int downvoteCount, java.util.List<String> tags, java.util.List<CommentView> comments)`
  - `CommentView(long id, Long authorId, String bodyMd, int upvoteCount, java.time.Instant createdAt)`
  - `CreateCommentRequest(String bodyMd)`
  - `PostService.createPost(long userId, CreatePostRequest req) -> PostDetailView`(FREE/FEEDBACK만) · `postDetail(long postId) -> PostDetailView`
  - `CommentService.addComment(long userId, long postId, CreateCommentRequest req) -> CommentView` · `listComments(long postId) -> List<CommentView>`

- [ ] **Step 1: DTO 4종 작성**

`CreatePostRequest.java`:
```java
package ai.devpath.community.post.dto;
import java.util.List;
public record CreatePostRequest(String boardType, String title, String bodyMd, List<String> tags) {}
```
`PostDetailView.java`:
```java
package ai.devpath.community.post.dto;
import java.util.List;
public record PostDetailView(long id, String boardType, String title, String bodyMd, Long authorId,
    int upvoteCount, int downvoteCount, List<String> tags, List<CommentView> comments) {}
```
`CommentView.java`:
```java
package ai.devpath.community.post.dto;
import java.time.Instant;
public record CommentView(long id, Long authorId, String bodyMd, int upvoteCount, Instant createdAt) {}
```
`CreateCommentRequest.java`:
```java
package ai.devpath.community.post.dto;
public record CreateCommentRequest(String bodyMd) {}
```

- [ ] **Step 2: 실패 테스트 작성**

`PostServiceTest.java`(실 DB 슬라이스):
```java
package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.devpath.community.post.dto.CreatePostRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PostServiceTest {

  @Autowired PostService postService;
  @Autowired CommunityQuestionRepository questions;

  @Test
  void createFreePost_savesWithoutQuestionRow() {
    var view = postService.createPost(10L,
        new CreatePostRequest("FREE", "자유 글", "자유 본문", List.of("잡담")));
    assertThat(view.boardType()).isEqualTo("FREE");
    assertThat(view.title()).isEqualTo("자유 글");
    assertThat(view.comments()).isEmpty();
    assertThat(questions.findById(view.id())).isEmpty(); // Q&A row 없음
  }

  @Test
  void createFeedbackPost_ok() {
    var view = postService.createPost(11L,
        new CreatePostRequest("FEEDBACK", "피드백 요청", "코드 봐주세요", List.of()));
    assertThat(view.boardType()).isEqualTo("FEEDBACK");
  }

  @Test
  void createRejectsQnaAndUnknownBoard() {
    assertThatThrownBy(() -> postService.createPost(12L,
        new CreatePostRequest("QNA", "t", "b", List.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> postService.createPost(12L,
        new CreatePostRequest("STUDY", "t", "b", List.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void postDetail_missingPost_throwsNotFound() {
    assertThatThrownBy(() -> postService.postDetail(999999L))
        .isInstanceOf(NotFoundException.class);
  }
}
```

`CommentServiceTest.java`:
```java
package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.devpath.community.post.dto.CreateCommentRequest;
import ai.devpath.community.post.dto.CreatePostRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CommentServiceTest {

  @Autowired PostService postService;
  @Autowired CommentService commentService;

  @Test
  void addAndListComments_inOrder() {
    long postId = postService.createPost(20L,
        new CreatePostRequest("FREE", "글", "본문", List.of())).id();
    commentService.addComment(21L, postId, new CreateCommentRequest("댓글1"));
    commentService.addComment(22L, postId, new CreateCommentRequest("댓글2"));

    List<?> list = commentService.listComments(postId);
    assertThat(list).hasSize(2);
    assertThat(postService.postDetail(postId).comments()).hasSize(2);
  }

  @Test
  void addComment_missingPost_throwsNotFound() {
    assertThatThrownBy(() -> commentService.addComment(20L, 999999L, new CreateCommentRequest("x")))
        .isInstanceOf(NotFoundException.class);
  }
}
```

- [ ] **Step 3: 실패 확인**

Run: `cd /d/workspace/dpa/devpath-community-svc && ./gradlew test --tests "ai.devpath.community.post.PostServiceTest" --tests "ai.devpath.community.post.CommentServiceTest"`
Expected: FAIL(PostService/CommentService 없음).

- [ ] **Step 4: PostService 구현**

`PostService.java`:
```java
package ai.devpath.community.post;

import ai.devpath.community.post.dto.CommentView;
import ai.devpath.community.post.dto.CreatePostRequest;
import ai.devpath.community.post.dto.PostDetailView;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {
  private static final Set<String> GENERAL_BOARDS = Set.of("FREE", "FEEDBACK");

  private final CommunityPostRepository posts;
  private final CommunityTagRepository tags;
  private final CommunityPostTagRepository postTags;
  private final CommunityCommentRepository comments;

  public PostService(CommunityPostRepository posts, CommunityTagRepository tags,
      CommunityPostTagRepository postTags, CommunityCommentRepository comments) {
    this.posts = posts; this.tags = tags; this.postTags = postTags; this.comments = comments;
  }

  @Transactional
  public PostDetailView createPost(long userId, CreatePostRequest req) {
    String board = req.boardType();
    if (board == null || !GENERAL_BOARDS.contains(board)) {
      throw new IllegalArgumentException("boardType must be FREE or FEEDBACK: " + board);
    }
    CommunityPost p = new CommunityPost();
    p.setAuthorId(userId); p.setBoardType(board);
    p.setTitle(req.title()); p.setBodyMd(req.bodyMd()); p.setStatus("PUBLISHED");
    p = posts.save(p);
    List<String> tagNames = req.tags() == null ? List.of() : req.tags();
    for (String name : tagNames) {
      CommunityTag tag = tags.findByName(name).orElseGet(() -> {
        CommunityTag t = new CommunityTag(); t.setName(name); return tags.save(t);
      });
      postTags.save(new CommunityPostTag(p.getId(), tag.getId()));
    }
    return postDetail(p.getId());
  }

  @Transactional(readOnly = true)
  public PostDetailView postDetail(long postId) {
    CommunityPost p = posts.findById(postId)
        .orElseThrow(() -> new NotFoundException("post " + postId));
    List<String> tagNames = tagNamesFor(postId);
    List<CommentView> commentViews = comments.findByPostIdOrderByCreatedAtAsc(postId).stream()
        .map(c -> new CommentView(c.getId(), c.getAuthorId(), c.getBodyMd(),
            c.getUpvoteCount(), c.getCreatedAt()))
        .collect(Collectors.toList());
    return new PostDetailView(p.getId(), p.getBoardType(), p.getTitle(), p.getBodyMd(),
        p.getAuthorId(), p.getUpvoteCount(), p.getDownvoteCount(), tagNames, commentViews);
  }

  private List<String> tagNamesFor(long postId) {
    List<Long> ids = postTags.findByPostId(postId).stream()
        .map(CommunityPostTag::getTagId).collect(Collectors.toList());
    if (ids.isEmpty()) return List.of();
    return tags.findAllById(ids).stream().map(CommunityTag::getName).collect(Collectors.toList());
  }
}
```

`CommentService.java`:
```java
package ai.devpath.community.post;

import ai.devpath.community.post.dto.CommentView;
import ai.devpath.community.post.dto.CreateCommentRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
  private final CommunityPostRepository posts;
  private final CommunityCommentRepository comments;

  public CommentService(CommunityPostRepository posts, CommunityCommentRepository comments) {
    this.posts = posts; this.comments = comments;
  }

  @Transactional
  public CommentView addComment(long userId, long postId, CreateCommentRequest req) {
    if (posts.findById(postId).isEmpty()) {
      throw new NotFoundException("post " + postId);
    }
    CommunityComment c = new CommunityComment();
    c.setPostId(postId); c.setAuthorId(userId); c.setBodyMd(req.bodyMd());
    c = comments.save(c);
    return new CommentView(c.getId(), c.getAuthorId(), c.getBodyMd(), c.getUpvoteCount(),
        c.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public List<CommentView> listComments(long postId) {
    return comments.findByPostIdOrderByCreatedAtAsc(postId).stream()
        .map(c -> new CommentView(c.getId(), c.getAuthorId(), c.getBodyMd(), c.getUpvoteCount(),
            c.getCreatedAt()))
        .collect(Collectors.toList());
  }
}
```

- [ ] **Step 5: 통과 확인**

Run: `cd /d/workspace/dpa/devpath-community-svc && ./gradlew test --tests "ai.devpath.community.post.PostServiceTest" --tests "ai.devpath.community.post.CommentServiceTest"`
Expected: PASS. (`CommunityTagRepository.findByName`·`CommunityPostTagRepository.findByPostId`가 QuestionService에서 이미 쓰이므로 존재.)

- [ ] **Step 6: 커밋**

```bash
git -C D:/workspace/dpa/devpath-community-svc add \
  src/main/java/ai/devpath/community/post/dto/CreatePostRequest.java \
  src/main/java/ai/devpath/community/post/dto/PostDetailView.java \
  src/main/java/ai/devpath/community/post/dto/CommentView.java \
  src/main/java/ai/devpath/community/post/dto/CreateCommentRequest.java \
  src/main/java/ai/devpath/community/post/PostService.java \
  src/main/java/ai/devpath/community/post/CommentService.java \
  src/test/java/ai/devpath/community/post/PostServiceTest.java \
  src/test/java/ai/devpath/community/post/CommentServiceTest.java
git -C D:/workspace/dpa/devpath-community-svc commit -m "feat(community): 일반 게시글 PostService + 댓글 CommentService(평판 미발행)"
```

---

### Task 4: CommunityController 엔드포인트 + MockMvc 테스트

**Files:**
- Modify: `src/main/java/ai/devpath/community/post/CommunityController.java`
- Test: `src/test/java/ai/devpath/community/post/FreeBoardMockMvcTest.java`

**Interfaces:**
- Consumes: Task 3 `PostService`·`CommentService` + DTO.
- Produces: `POST /community/posts`, `GET /community/posts/{id}`, `POST /community/posts/{id}/comments`, `GET /community/posts/{id}/comments`.

- [ ] **Step 1: 실패 테스트 작성**

`FreeBoardMockMvcTest.java`:
```java
package ai.devpath.community.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FreeBoardMockMvcTest {

  @Autowired MockMvc mvc;

  @Test
  void createFreePost_thenDetail_thenComment() throws Exception {
    String body = mvc.perform(post("/community/posts")
            .with(jwt().jwt(j -> j.subject("300")))
            .contentType("application/json")
            .content("{\"boardType\":\"FREE\",\"title\":\"자유글\",\"bodyMd\":\"본문\",\"tags\":[\"잡담\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.boardType").value("FREE"))
        .andExpect(jsonPath("$.title").value("자유글"))
        .andExpect(jsonPath("$.comments.length()").value(0))
        .andReturn().getResponse().getContentAsString();
    long id = com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);

    mvc.perform(post("/community/posts/" + id + "/comments")
            .with(jwt().jwt(j -> j.subject("301")))
            .contentType("application/json").content("{\"bodyMd\":\"댓글내용\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.bodyMd").value("댓글내용"));

    mvc.perform(get("/community/posts/" + id).with(jwt().jwt(j -> j.subject("300"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bodyMd").value("본문"))
        .andExpect(jsonPath("$.comments.length()").value(1));
  }

  @Test
  void createQnaViaPostEndpoint_rejected() throws Exception {
    mvc.perform(post("/community/posts").with(jwt().jwt(j -> j.subject("302")))
            .contentType("application/json")
            .content("{\"boardType\":\"QNA\",\"title\":\"t\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void unauthenticatedRejected() throws Exception {
    mvc.perform(post("/community/posts").contentType("application/json")
        .content("{\"boardType\":\"FREE\",\"title\":\"t\",\"bodyMd\":\"b\",\"tags\":[]}"))
        .andExpect(status().isUnauthorized());
  }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /d/workspace/dpa/devpath-community-svc && ./gradlew test --tests "ai.devpath.community.post.FreeBoardMockMvcTest"`
Expected: FAIL(엔드포인트 없음 → 404/405).

- [ ] **Step 3: 컨트롤러에 필드·엔드포인트 추가**

`CommunityController.java` 수정:
- 생성자와 필드에 `PostService postService`·`CommentService commentService` 추가(기존 필드/생성자에 병합 — 아래 import 추가 `ai.devpath.community.post.dto.*`는 이미 있음).
- 기존 `GET /posts`(목록) 아래에 다음 메서드 추가:

```java
  @PostMapping("/posts")
  public ResponseEntity<PostDetailView> createPost(
      @AuthenticationPrincipal Jwt jwt, @RequestBody CreatePostRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(uid(jwt), req));
  }

  @GetMapping("/posts/{id}")
  public ResponseEntity<PostDetailView> postDetail(@PathVariable long id) {
    return ResponseEntity.ok(postService.postDetail(id));
  }

  @PostMapping("/posts/{id}/comments")
  public ResponseEntity<CommentView> addComment(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody CreateCommentRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(commentService.addComment(uid(jwt), id, req));
  }

  @GetMapping("/posts/{id}/comments")
  public ResponseEntity<java.util.List<CommentView>> listComments(@PathVariable long id) {
    return ResponseEntity.ok(commentService.listComments(id));
  }
```

생성자 수정 예(기존 6개 파라미터 + 2개 추가):
```java
  public CommunityController(QuestionService questionService, AnswerService answerService,
      VoteService voteService, TagService tagService, EmbeddingClient embeddingClient,
      SimilarQuestionMatcher similarQuestionMatcher,
      PostService postService, CommentService commentService) {
    this.questionService = questionService; this.answerService = answerService;
    this.voteService = voteService; this.tagService = tagService;
    this.embeddingClient = embeddingClient; this.similarQuestionMatcher = similarQuestionMatcher;
    this.postService = postService; this.commentService = commentService;
  }
```
(필드 `private final PostService postService;`·`private final CommentService commentService;` 선언 추가.)

- [ ] **Step 4: 통과 확인 + 전체 빌드**

Run: `cd /d/workspace/dpa/devpath-community-svc && ./gradlew test --tests "ai.devpath.community.post.FreeBoardMockMvcTest"` → PASS.
Run: `cd /d/workspace/dpa/devpath-community-svc && ./gradlew build` → BUILD SUCCESSFUL(기존 Q&A/vote/tag 등 전체 green). 실패 시 근본원인 규명(추측 금지).

- [ ] **Step 5: 커밋**

```bash
git -C D:/workspace/dpa/devpath-community-svc add \
  src/main/java/ai/devpath/community/post/CommunityController.java \
  src/test/java/ai/devpath/community/post/FreeBoardMockMvcTest.java
git -C D:/workspace/dpa/devpath-community-svc commit -m "feat(community): FREE/FEEDBACK 게시글·댓글 REST 엔드포인트"
```

---

### Task 5: 정리 + PR (shared, community-svc)

- [ ] **Step 1: 로컬 스캐폴딩 원복 확인**

`build.gradle.kts`의 임시 `mavenLocal()`이 **커밋되지 않았는지** 확인하고 원복: `git -C D:/workspace/dpa/devpath-community-svc diff -- build.gradle.kts`가 비어야 함(아니면 해당 줄 되돌림). `git -C D:/workspace/dpa/devpath-community-svc status`에 build.gradle.kts 변경 없음 확인.

- [ ] **Step 2: shared push + develop PR**

```bash
git -C D:/workspace/dpa/devpath-shared push -u origin feat/community-comments-migration
gh -C D:/workspace/dpa/devpath-shared 2>/dev/null || (cd /d/workspace/dpa/devpath-shared && gh pr create --base develop --head feat/community-comments-migration \
  --title "feat(db): 커뮤니티 FEEDBACK 보드 + community_comments" \
  --body "community-svc FREE/FEEDBACK 보드+댓글용. board_type에 FEEDBACK 추가 + community_comments 테이블. spec: devpath-community-svc/docs/superpowers/specs/2026-07-29-community-free-feedback-boards-design.md")
```

- [ ] **Step 3: community-svc push + develop PR**

```bash
cd /d/workspace/dpa/devpath-community-svc && git push -u origin feat/community-free-feedback-boards
gh pr create --base develop --head feat/community-free-feedback-boards \
  --title "feat(community): FREE/FEEDBACK 보드 + 댓글 (백엔드)" \
  --body "일반 게시글 PostService/CommentService(Q&A 분리·평판 미발행) + 엔드포인트. shared 마이그(FEEDBACK enum + community_comments) 선행 PR 필요. spec/plan docs/superpowers/{specs,plans}/2026-07-29-community-free-feedback-boards*."
```
**주의**: community-svc PR CI는 shared 발행본에 새 마이그가 있어야 green. shared PR 머지 + SNAPSHOT 발행(`gh workflow run publish.yml --ref develop`) 후 community-svc CI 재실행. 순서=shared 먼저.

---

## Self-Review

**1. Spec coverage:** §3.1 마이그=Task1, §3.2 엔티티/리포=Task2, §3.3 서비스=Task3, §3.4 컨트롤러=Task4, §3.5 DTO=Task3 Step1, §4 테스트=각 Task 테스트+Task4 전체빌드, §6 롤아웃(shared 먼저·발행)=Task1·Task5. ✅

**2. Placeholder scan:** 모든 코드/SQL/명령 실제값. 컨트롤러 수정은 추가 메서드·생성자·필드를 구체 제시. TBD 없음. ✅

**3. Type consistency:** `CreatePostRequest(boardType,title,bodyMd,tags)`·`PostDetailView(...comments)`·`CommentView(id,authorId,bodyMd,upvoteCount,createdAt:Instant)`·`CreateCommentRequest(bodyMd)`, `PostService.createPost/postDetail`·`CommentService.addComment/listComments`, `CommunityComment` getter/setter, `CommunityCommentRepository.findByPostIdOrderByCreatedAtAsc/countByPostId`가 Task 간 일치. 기존 `CommunityPost` setter(setAuthorId/setBoardType/setTitle/setBodyMd/setStatus)·`CommunityTagRepository.findByName`·`CommunityPostTagRepository.findByPostId`·`CommunityPostTag(postId,tagId)` 실측 일치. ✅

**4. 리스크:** shared 로컬 소비(mavenLocal 스캐폴딩·미커밋)·devpath_citest DB 생성 명시. 경로 충돌(GET/POST /posts, /posts/{id} vs /questions/{id}) HTTP 메서드·경로로 구분. Task4 전체빌드가 회귀 가드. ✅

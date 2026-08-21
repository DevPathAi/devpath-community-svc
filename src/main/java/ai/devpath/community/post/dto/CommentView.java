package ai.devpath.community.post.dto;

import java.time.Instant;

/**
 * 댓글 표현. {@code deleted=true} 면 비석이다 — 본문과 작성자를 비운다.
 *
 * <p>작성 시각은 남긴다. 스레드 순서가 보이지 않으면 비석의 의미가 없다.
 */
public record CommentView(long id, Long authorId, String bodyMd, int upvoteCount,
    Instant createdAt, boolean deleted) {

  public static CommentView tombstone(long id, int upvoteCount, Instant createdAt) {
    return new CommentView(id, null, null, upvoteCount, createdAt, true);
  }
}

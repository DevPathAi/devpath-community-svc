package ai.devpath.community.post.dto;

/**
 * 답변 표현. {@code deleted=true} 면 비석이다 — 본문과 작성자를 비운다.
 *
 * <p>비석의 목적은 스레드 맥락 보존이지 "누가 썼다 지웠다" 의 기록이 아니므로 작성자도 감춘다.
 * 집계(upvoteCount)는 남긴다 — 작성자 삭제는 평판을 유지하기로 했으므로 일관된다.
 */
public record AnswerView(long id, Long authorId, String bodyMd, boolean aiGenerated,
    boolean accepted, int upvoteCount, boolean deleted) {

  public static AnswerView tombstone(long id, int upvoteCount) {
    return new AnswerView(id, null, null, false, false, upvoteCount, true);
  }
}

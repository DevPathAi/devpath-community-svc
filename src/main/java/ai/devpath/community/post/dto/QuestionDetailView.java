package ai.devpath.community.post.dto;

import java.util.List;

/**
 * 질문 상세. {@code authorId} 는 {@code PostDetailView} 와 같은 자리(본문 다음)에 둔다 —
 * 프론트가 「내 질문인가」를 판단하는 유일한 근거다.
 */
public record QuestionDetailView(long id, String title, String bodyMd, Long authorId,
    boolean solved, Long acceptedAnswerId, int upvoteCount, int downvoteCount,
    List<String> tags, List<AnswerView> answers) {}

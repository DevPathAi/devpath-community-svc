package ai.devpath.community.post.dto;

import java.util.List;

public record QuestionDetailView(long id, String title, String bodyMd, boolean solved,
    Long acceptedAnswerId, int upvoteCount, int downvoteCount, List<String> tags,
    List<AnswerView> answers) {}

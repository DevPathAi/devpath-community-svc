package ai.devpath.community.post.dto;

public record AnswerView(long id, Long authorId, String bodyMd, boolean aiGenerated,
    boolean accepted, int upvoteCount) {}

package ai.devpath.community.post.dto;

public record PostSummaryView(long id, String boardType, String title, Long authorId,
    boolean solved, int upvoteCount, int replyCount) {}

package ai.devpath.community.post.dto;
import java.time.Instant;
public record CommentView(long id, Long authorId, String bodyMd, int upvoteCount, Instant createdAt) {}

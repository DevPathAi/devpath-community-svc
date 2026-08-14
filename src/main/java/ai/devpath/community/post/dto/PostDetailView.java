package ai.devpath.community.post.dto;
import java.util.List;
public record PostDetailView(long id, String boardType, String title, String bodyMd, Long authorId,
    int upvoteCount, int downvoteCount, List<String> tags, List<CommentView> comments) {}

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
    // 부모가 비공개면 자식으로 우회 조회할 수 없어야 한다.
    posts.findById(postId)
        .filter(p -> ContentStatus.PUBLISHED.equals(p.getStatus()))
        .orElseThrow(() -> new NotFoundException("post " + postId));
    return comments.findByPostIdOrderByCreatedAtAsc(postId).stream()
        .map(c -> new CommentView(c.getId(), c.getAuthorId(), c.getBodyMd(), c.getUpvoteCount(),
            c.getCreatedAt()))
        .collect(Collectors.toList());
  }
}

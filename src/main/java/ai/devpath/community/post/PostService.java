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
  private final PostIndexEventPublisher postIndexEvents;

  public PostService(CommunityPostRepository posts, CommunityTagRepository tags,
      CommunityPostTagRepository postTags, CommunityCommentRepository comments,
      PostIndexEventPublisher postIndexEvents) {
    this.posts = posts; this.tags = tags; this.postTags = postTags; this.comments = comments;
    this.postIndexEvents = postIndexEvents;
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
    postIndexEvents.publish(p.getId(), false);
    return postDetail(p.getId());
  }

  @Transactional(readOnly = true)
  public PostDetailView postDetail(long postId) {
    CommunityPost p = posts.findById(postId)
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
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

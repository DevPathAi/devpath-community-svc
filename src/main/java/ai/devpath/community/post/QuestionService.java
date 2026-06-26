package ai.devpath.community.post;

import ai.devpath.community.post.dto.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {
  private final CommunityPostRepository posts;
  private final CommunityQuestionRepository questions;
  private final CommunityAnswerRepository answers;
  private final CommunityTagRepository tags;
  private final CommunityPostTagRepository postTags;

  public QuestionService(CommunityPostRepository posts, CommunityQuestionRepository questions,
      CommunityAnswerRepository answers, CommunityTagRepository tags,
      CommunityPostTagRepository postTags) {
    this.posts = posts; this.questions = questions; this.answers = answers;
    this.tags = tags; this.postTags = postTags;
  }

  @Transactional
  public QuestionDetailView create(long userId, CreateQuestionRequest req) {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(userId); p.setBoardType("QNA");
    p.setTitle(req.title()); p.setBodyMd(req.bodyMd()); p.setStatus("PUBLISHED");
    p = posts.save(p);
    CommunityQuestion q = new CommunityQuestion();
    q.setPostId(p.getId());
    questions.save(q);
    List<String> tagNames = req.tags() == null ? List.of() : req.tags();
    for (String name : tagNames) {
      CommunityTag tag = tags.findByName(name).orElseGet(() -> {
        CommunityTag t = new CommunityTag(); t.setName(name); return tags.save(t);
      });
      postTags.save(new CommunityPostTag(p.getId(), tag.getId()));
    }
    return detail(p.getId());
  }

  @Transactional(readOnly = true)
  public QuestionDetailView detail(long postId) {
    CommunityPost p = posts.findById(postId)
        .orElseThrow(() -> new NotFoundException("question " + postId));
    CommunityQuestion q = questions.findById(postId)
        .orElseThrow(() -> new NotFoundException("question " + postId));
    List<AnswerView> ans = answers.findByQuestionIdOrderByCreatedAtAsc(postId).stream()
        .map(a -> new AnswerView(a.getId(), a.getAuthorId(), a.getBodyMd(),
            a.isAiGenerated(), a.isAccepted(), a.getUpvoteCount()))
        .collect(Collectors.toList());
    List<String> tagNames = tagNamesFor(postId);
    return new QuestionDetailView(p.getId(), p.getTitle(), p.getBodyMd(), q.isSolved(),
        q.getAcceptedAnswerId(), p.getUpvoteCount(), p.getDownvoteCount(), tagNames, ans);
  }

  @Transactional(readOnly = true)
  public List<PostSummaryView> list(String board, String tag, String sort) {
    String b = (board == null || board.isBlank()) ? "QNA" : board;
    return posts.findBoardNewest(b).stream()
        .map(p -> new PostSummaryView(p.getId(), p.getTitle(), p.getAuthorId(),
            questions.findById(p.getId()).map(CommunityQuestion::isSolved).orElse(false),
            p.getUpvoteCount(), answers.countByQuestionId(p.getId())))
        .collect(Collectors.toList());
  }

  private List<String> tagNamesFor(long postId) {
    List<Long> ids = postTags.findByPostId(postId).stream()
        .map(CommunityPostTag::getTagId).collect(Collectors.toList());
    if (ids.isEmpty()) return List.of();
    return tags.findAllById(ids).stream().map(CommunityTag::getName).collect(Collectors.toList());
  }
}

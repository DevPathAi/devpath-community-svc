package ai.devpath.community.post;

import ai.devpath.community.badge.BadgeCode;
import ai.devpath.community.badge.BadgeService;
import ai.devpath.community.outbox.OutboxEntry;
import ai.devpath.community.outbox.OutboxRepository;
import ai.devpath.community.post.dto.*;
import ai.devpath.shared.event.CommunityQuestionPostedEvent;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class QuestionService {
  private final CommunityPostRepository posts;
  private final CommunityQuestionRepository questions;
  private final CommunityAnswerRepository answers;
  private final CommunityTagRepository tags;
  private final CommunityPostTagRepository postTags;
  private final OutboxRepository outbox;
  private final JsonMapper jsonMapper;
  private final BadgeService badgeService;

  public QuestionService(CommunityPostRepository posts, CommunityQuestionRepository questions,
      CommunityAnswerRepository answers, CommunityTagRepository tags,
      CommunityPostTagRepository postTags, OutboxRepository outbox, JsonMapper jsonMapper,
      BadgeService badgeService) {
    this.posts = posts; this.questions = questions; this.answers = answers;
    this.tags = tags; this.postTags = postTags;
    this.outbox = outbox; this.jsonMapper = jsonMapper;
    this.badgeService = badgeService;
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
    publishQuestionPosted(userId, p.getId(), req);
    badgeService.award(userId, BadgeCode.FIRST_QUESTION, "POST", p.getId());
    return detail(p.getId());
  }

  /** 질문 게시 이벤트를 같은 트랜잭션의 Outbox에 적재(설계 D-1). questionId == postId(question PK = post id). */
  private void publishQuestionPosted(long userId, long postId, CreateQuestionRequest req) {
    CommunityQuestionPostedEvent event = new CommunityQuestionPostedEvent(
        UUID.randomUUID(), Instant.now(), userId, postId, postId, req.title(), req.bodyMd());
    OutboxEntry entry = new OutboxEntry();
    entry.setAggregateType("community_question");
    entry.setAggregateId(String.valueOf(postId));
    entry.setEventType(CommunityQuestionPostedEvent.EVENT_TYPE);
    entry.setPayload(jsonMapper.writeValueAsString(event));
    entry.setCreatedAt(Instant.now());
    outbox.save(entry);
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

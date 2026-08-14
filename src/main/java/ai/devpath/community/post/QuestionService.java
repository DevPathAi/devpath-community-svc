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
  private final CommunityCommentRepository comments;
  private final CommunityTagRepository tags;
  private final CommunityPostTagRepository postTags;
  private final OutboxRepository outbox;
  private final JsonMapper jsonMapper;
  private final BadgeService badgeService;
  private final PostIndexEventPublisher postIndexEvents;

  public QuestionService(CommunityPostRepository posts, CommunityQuestionRepository questions,
      CommunityAnswerRepository answers, CommunityCommentRepository comments,
      CommunityTagRepository tags, CommunityPostTagRepository postTags, OutboxRepository outbox,
      JsonMapper jsonMapper, BadgeService badgeService, PostIndexEventPublisher postIndexEvents) {
    this.posts = posts; this.questions = questions; this.answers = answers;
    this.comments = comments;
    this.tags = tags; this.postTags = postTags;
    this.outbox = outbox; this.jsonMapper = jsonMapper;
    this.badgeService = badgeService;
    this.postIndexEvents = postIndexEvents;
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
    postIndexEvents.publish(p.getId(), false);
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
    List<CommunityPost> found = (board == null || board.isBlank() || "ALL".equals(board))
        ? posts.findAllBoardsNewest()
        : posts.findBoardNewest(board);
    return found.stream()
        .map(this::toSummary)
        .collect(Collectors.toList());
  }

  /** 글 목록을 표시용 요약으로 조립한다. QNA 는 답변 수·해결 여부, 그 외는 댓글 수를 센다. */
  private PostSummaryView toSummary(CommunityPost p) {
    boolean isQna = "QNA".equals(p.getBoardType());
    int replyCount = isQna
        ? (int) answers.countByQuestionId(p.getId())
        : (int) comments.countByPostId(p.getId());
    boolean solved = isQna
        ? questions.findById(p.getId()).map(CommunityQuestion::isSolved).orElse(false)
        : false;
    return new PostSummaryView(p.getId(), p.getBoardType(), p.getTitle(),
        p.getAuthorId(), solved, p.getUpvoteCount(), replyCount,
        Excerpts.from(p.getBodyMd(), 140));
  }

  /** 검색 결과 조립용 — 입력 id 순서(관련도 순)를 그대로 보존한다. */
  @Transactional(readOnly = true)
  public List<PostSummaryView> summariesByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<Long, CommunityPost> byId = posts.findAllById(ids).stream()
        .collect(Collectors.toMap(CommunityPost::getId, p -> p));
    return ids.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(this::toSummary)
        .collect(Collectors.toList());
  }

  private List<String> tagNamesFor(long postId) {
    List<Long> ids = postTags.findByPostId(postId).stream()
        .map(CommunityPostTag::getTagId).collect(Collectors.toList());
    if (ids.isEmpty()) return List.of();
    return tags.findAllById(ids).stream().map(CommunityTag::getName).collect(Collectors.toList());
  }
}

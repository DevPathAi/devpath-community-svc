package ai.devpath.community.post;

import ai.devpath.community.badge.BadgeCode;
import ai.devpath.community.badge.BadgeService;
import ai.devpath.community.post.dto.AnswerView;
import ai.devpath.community.post.dto.CreateAnswerRequest;
import ai.devpath.community.reputation.RepPoints;
import ai.devpath.community.reputation.ReputationService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerService {
  private final CommunityPostRepository posts;
  private final CommunityQuestionRepository questions;
  private final CommunityAnswerRepository answers;
  private final ReputationService reputation;
  private final CommunityPostTagRepository postTags;
  private final BadgeService badgeService;

  public AnswerService(CommunityPostRepository posts, CommunityQuestionRepository questions,
      CommunityAnswerRepository answers, ReputationService reputation,
      CommunityPostTagRepository postTags, BadgeService badgeService) {
    this.posts = posts; this.questions = questions; this.answers = answers;
    this.reputation = reputation; this.postTags = postTags;
    this.badgeService = badgeService;
  }

  @Transactional
  public AnswerView add(long userId, long questionId, CreateAnswerRequest req) {
    questions.findById(questionId).orElseThrow(() -> new NotFoundException("question " + questionId));
    CommunityAnswer a = new CommunityAnswer();
    a.setQuestionId(questionId); a.setAuthorId(userId); a.setBodyMd(req.bodyMd());
    a = answers.save(a);
    badgeService.award(userId, BadgeCode.FIRST_ANSWER, "ANSWER", a.getId());
    return new AnswerView(a.getId(), a.getAuthorId(), a.getBodyMd(),
        a.isAiGenerated(), a.isAccepted(), a.getUpvoteCount());
  }

  @Transactional
  public void accept(long userId, long answerId) {
    CommunityAnswer a = answers.findById(answerId)
        .orElseThrow(() -> new NotFoundException("answer " + answerId));
    if (a.isAccepted()) return;   // 중복 채택 가드(중복 가산 방지)
    CommunityQuestion q = questions.findById(a.getQuestionId())
        .orElseThrow(() -> new NotFoundException("question " + a.getQuestionId()));
    CommunityPost p = posts.findById(q.getPostId())
        .orElseThrow(() -> new NotFoundException("post " + q.getPostId()));
    if (p.getAuthorId() == null || p.getAuthorId() != userId) {
      throw new ForbiddenException("only question author can accept");
    }
    a.setAccepted(true);
    answers.save(a);
    q.setSolved(true);
    q.setAcceptedAnswerId(answerId);
    questions.save(q);

    List<Long> tagIds = postTags.findByPostId(q.getPostId()).stream()
        .map(CommunityPostTag::getTagId).toList();
    reputation.applyAcceptance(a.getAuthorId(), p.getAuthorId(), "ANSWER", answerId, tagIds);
    // 배지: 평판 15 도달 → PHILANTHROPIST(답변 작성자·질문 작성자 둘 다 평가)
    awardPhilanthropistIfReached(a.getAuthorId(), "ANSWER", answerId);
    awardPhilanthropistIfReached(p.getAuthorId(), "POST", q.getPostId());
  }

  private void awardPhilanthropistIfReached(long userId, String sourceType, long sourceId) {
    if (reputation.reputationOf(userId) >= RepPoints.LVL_UPVOTE_QUESTION) {
      badgeService.award(userId, BadgeCode.PHILANTHROPIST, sourceType, sourceId);
    }
  }
}

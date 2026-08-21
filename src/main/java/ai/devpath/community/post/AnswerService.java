package ai.devpath.community.post;

import ai.devpath.community.badge.BadgeCode;
import ai.devpath.community.badge.BadgeService;
import ai.devpath.community.post.dto.AnswerView;
import ai.devpath.community.post.dto.CreateAnswerRequest;
import ai.devpath.community.post.dto.UpdateBodyRequest;
import ai.devpath.community.report.ConflictException;
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
  private final ContentRevisionRecorder revisions;

  public AnswerService(CommunityPostRepository posts, CommunityQuestionRepository questions,
      CommunityAnswerRepository answers, ReputationService reputation,
      CommunityPostTagRepository postTags, BadgeService badgeService,
      ContentRevisionRecorder revisions) {
    this.posts = posts; this.questions = questions; this.answers = answers;
    this.reputation = reputation; this.postTags = postTags;
    this.badgeService = badgeService; this.revisions = revisions;
  }

  @Transactional
  public AnswerView add(long userId, long questionId, CreateAnswerRequest req) {
    questions.findById(questionId).orElseThrow(() -> new NotFoundException("question " + questionId));
    CommunityAnswer a = new CommunityAnswer();
    a.setQuestionId(questionId); a.setAuthorId(userId); a.setBodyMd(req.bodyMd());
    a = answers.save(a);
    badgeService.award(userId, BadgeCode.FIRST_ANSWER, "ANSWER", a.getId());
    return new AnswerView(a.getId(), a.getAuthorId(), a.getBodyMd(),
        a.isAiGenerated(), a.isAccepted(), a.getUpvoteCount(), false);
  }

  @Transactional
  public void accept(long userId, long answerId) {
    // 내려갔거나 지워진 답변은 채택 대상이 아니다. 막지 않으면 비석을 "정답" 으로 굳히고
    // (solved=true + acceptedAnswerId), 관리자가 회수한 평판을 채택 보상으로 되돌린다.
    CommunityAnswer a = answers.findByIdForUpdate(answerId)
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("answer " + answerId));
    if (a.isAccepted()) return;   // 중복 채택 가드(중복 가산 방지)
    // ★전역 락 순서: answer → question → post → (맨 마지막) 평판★
    // 답변 행 락은 "서로 다른 답변" 의 동시 채택을 직렬화하지 못한다 — 공유 상태는 질문
    // 행이다. 부모 글도 잠가 내리기와의 창을 닫는다(내려간 질문에 채택 보상이 나가면 안 된다).
    CommunityQuestion q = questions.findByIdForUpdate(a.getQuestionId())
        .orElseThrow(() -> new NotFoundException("question " + a.getQuestionId()));
    CommunityPost p = posts.findByIdForUpdate(q.getPostId())
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("post " + q.getPostId()));
    if (p.getAuthorId() == null || p.getAuthorId() != userId) {
      throw new ForbiddenException("only question author can accept");
    }
    // 다른 답변이 이미 채택돼 있으면 물러난다 — 덮어쓰면 두 답변이 accepted 로 남고
    // 보상이 두 번 나간다(경쟁뿐 아니라 순차로도 같은 구멍이었다).
    if (q.getAcceptedAnswerId() != null) {
      throw new ConflictException("이미 채택된 답변이 있습니다. 채택을 먼저 해제해 주세요.");
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

  @Transactional
  public AnswerView update(long userId, long answerId, UpdateBodyRequest req) {
    if (req.bodyMd() == null || req.bodyMd().isBlank()) {
      throw new IllegalArgumentException("bodyMd must not be blank");
    }
    // 잠그는 이유는 PostService.updatePost 와 같다 — 전 컬럼 flush 가 stale 상태(status·
    // accepted·집계)를 되돌려 쓴다.
    CommunityAnswer a = answers.findByIdForUpdate(answerId)
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("answer " + answerId));
    if (a.getAuthorId() == null || a.getAuthorId() != userId) {
      throw new ForbiddenException("작성자만 수정할 수 있습니다");
    }
    revisions.record("ANSWER", answerId, null, a.getBodyMd(), a.getBodyHtml(), userId);
    a.setBodyMd(req.bodyMd());
    answers.save(a);
    return new AnswerView(a.getId(), a.getAuthorId(), a.getBodyMd(),
        a.isAiGenerated(), a.isAccepted(), a.getUpvoteCount(), false);
  }

  /**
   * 작성자 삭제.
   *
   * <p>★수용된 답변은 지울 수 없다★ — 질문자가 "이게 정답" 이라고 공식화한 것이라 작성자가
   * 마음대로 지우면 질문이 깨진다. 409 로 돌려보내 먼저 수용을 해제하게 한다.
   * (관리자 삭제는 이 제한을 받지 않는다 — 규정 위반 답변이 하필 수용된 상태일 수 있다.)
   */
  @Transactional
  public void delete(long userId, long answerId) {
    CommunityAnswer a = answers.findByIdForUpdate(answerId)
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("answer " + answerId));
    if (a.getAuthorId() == null || a.getAuthorId() != userId) {
      throw new ForbiddenException("작성자만 삭제할 수 있습니다");
    }
    if (a.isAccepted()) {
      throw new ConflictException("채택된 답변입니다. 채택을 먼저 해제해 주세요.");
    }
    a.setStatus(ContentStatus.DELETED);
    answers.save(a);
  }

  private void awardPhilanthropistIfReached(long userId, String sourceType, long sourceId) {
    if (reputation.reputationOf(userId) >= RepPoints.LVL_UPVOTE_QUESTION) {
      badgeService.award(userId, BadgeCode.PHILANTHROPIST, sourceType, sourceId);
    }
  }
}

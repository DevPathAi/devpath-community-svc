package ai.devpath.community.reputation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 평판 가산 엔진. 호출자(VoteService/AnswerService)의 트랜잭션에 합류한다. */
@Service
public class ReputationService {
  private final ReputationEventRepository events;
  private final UserReputationRepository reputations;
  private final UserTagReputationRepository tagReputations;

  public ReputationService(ReputationEventRepository events, UserReputationRepository reputations,
      UserTagReputationRepository tagReputations) {
    this.events = events; this.reputations = reputations; this.tagReputations = tagReputations;
  }

  /** 투표 1건(생성/변경)의 평판 효과를 반영한다. oldValue/newValue ∈ {-1,0,1}. */
  @Transactional
  public void applyVote(long authorId, long voterId, String sourceType, long sourceId,
      int oldValue, int newValue, List<Long> tagIds) {
    if (oldValue == newValue) return;
    reverseVote(voterId, sourceType, sourceId, tagIds);   // 이전 효과 역산
    applyNewVote(authorId, voterId, sourceType, sourceId, newValue, tagIds);
  }

  private void reverseVote(long voterId, String sourceType, long sourceId, List<Long> tagIds) {
    List<ReputationEvent> prior =
        events.findByActorIdAndSourceTypeAndSourceId(voterId, sourceType, sourceId);
    for (ReputationEvent e : prior) {
      // 보상(역) 이벤트로 총점/태그 환원. 같은 사유로 -delta.
      int back = -e.getDelta();
      addTotal(e.getUserId(), back);
      // 작성자(수혜자) 이벤트면 태그도 환원(행사자 비용 DOWNVOTE_CAST는 태그 무관).
      if (!ReputationReason.DOWNVOTE_CAST.name().equals(e.getReason())) {
        for (Long tagId : tagIds) addTag(e.getUserId(), tagId, back);
      }
      events.save(new ReputationEvent(e.getUserId(), voterId, back,
          ReputationReason.valueOf(e.getReason()), sourceType, sourceId));
    }
  }

  private void applyNewVote(long authorId, long voterId, String sourceType, long sourceId,
      int newValue, List<Long> tagIds) {
    if (newValue == 1) {
      ReputationReason reason = "ANSWER".equals(sourceType)
          ? ReputationReason.UPVOTE_A : ReputationReason.UPVOTE_Q;
      int nominal = "ANSWER".equals(sourceType) ? RepPoints.UPVOTE_ANSWER : RepPoints.UPVOTE_QUESTION;
      int granted = capDailyUpvote(authorId, nominal);
      if (granted != 0) {
        addTotal(authorId, granted);
        for (Long tagId : tagIds) addTag(authorId, tagId, granted);
        events.save(new ReputationEvent(authorId, voterId, granted, reason, sourceType, sourceId));
      }
    } else if (newValue == -1) {
      // 작성자: downvote 받음 -2
      addTotal(authorId, RepPoints.DOWNVOTE_RECEIVED);
      for (Long tagId : tagIds) addTag(authorId, tagId, RepPoints.DOWNVOTE_RECEIVED);
      events.save(new ReputationEvent(authorId, voterId, RepPoints.DOWNVOTE_RECEIVED,
          ReputationReason.DOWNVOTE_RECEIVED, sourceType, sourceId));
      // 행사자: downvote 비용 -1 (태그 무관)
      addTotal(voterId, RepPoints.DOWNVOTE_CAST);
      events.save(new ReputationEvent(voterId, voterId, RepPoints.DOWNVOTE_CAST,
          ReputationReason.DOWNVOTE_CAST, sourceType, sourceId));
    }
  }

  /** upvote 명목치를, 작성자의 오늘자 upvote 획득합이 40을 넘지 않도록 클램프. */
  private int capDailyUpvote(long authorId, int nominal) {
    Instant since = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
    int todays = events.sumUpvoteGainSince(authorId, since);
    int room = RepPoints.DAILY_UPVOTE_CAP - todays;
    if (room <= 0) return 0;
    return Math.min(nominal, room);
  }

  @Transactional
  public void applyAcceptance(long answerAuthorId, long questionAuthorId, String sourceType,
      long sourceId, List<Long> tagIds) {
    addTotal(answerAuthorId, RepPoints.ACCEPTED);
    for (Long tagId : tagIds) addTag(answerAuthorId, tagId, RepPoints.ACCEPTED);
    events.save(new ReputationEvent(answerAuthorId, questionAuthorId, RepPoints.ACCEPTED,
        ReputationReason.ACCEPTED, sourceType, sourceId));

    addTotal(questionAuthorId, RepPoints.ACCEPT_BONUS);
    for (Long tagId : tagIds) addTag(questionAuthorId, tagId, RepPoints.ACCEPT_BONUS);
    events.save(new ReputationEvent(questionAuthorId, questionAuthorId, RepPoints.ACCEPT_BONUS,
        ReputationReason.ACCEPT_BONUS, sourceType, sourceId));
  }

  public int reputationOf(long userId) {
    return reputations.findByUserId(userId).map(UserReputation::getTotal).orElse(0);
  }

  private void addTotal(long userId, int delta) {
    UserReputation r = reputations.findByUserId(userId).orElseGet(() -> new UserReputation(userId));
    r.add(delta);
    reputations.save(r);
  }

  private void addTag(long userId, long tagId, int delta) {
    UserTagReputation t = tagReputations.findByUserIdAndTagId(userId, tagId)
        .orElseGet(() -> new UserTagReputation(userId, tagId));
    t.add(delta);
    tagReputations.save(t);
  }
}

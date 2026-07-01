package ai.devpath.community.abuse;

import ai.devpath.community.outbox.OutboxEntry;
import ai.devpath.community.outbox.OutboxRepository;
import ai.devpath.community.reputation.RepPoints;
import ai.devpath.community.reputation.ReputationEventRepository;
import ai.devpath.shared.event.CommunityReputationSuspectedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 행동 기반 담합(sockpuppet) 탐지 — 기록만. 호출자(VoteService)의 트랜잭션에 합류한다. */
@Service
public class CollusionDetector {
  public static final String REASON_REPEAT_UPVOTE = "REPEAT_UPVOTE";

  private final ReputationEventRepository events;
  private final VoteAbuseSuspicionRepository suspicions;
  private final OutboxRepository outbox;
  private final JsonMapper jsonMapper;

  public CollusionDetector(ReputationEventRepository events, VoteAbuseSuspicionRepository suspicions,
      OutboxRepository outbox, JsonMapper jsonMapper) {
    this.events = events; this.suspicions = suspicions;
    this.outbox = outbox; this.jsonMapper = jsonMapper;
  }

  /** upvote 직후 호출. voter가 author의 서로 다른 글을 임계 이상 upvote했으면 의심 1회 기록 + 이벤트. */
  @Transactional
  public void checkOnUpvote(long voterId, long authorId, String sourceType, long sourceId) {
    long distinct = events.countDistinctUpvotedSourcesByActorToUser(voterId, authorId);
    if (distinct < RepPoints.COLLUSION_UPVOTE_THRESHOLD) return;
    if (suspicions.existsByActorIdAndTargetUserIdAndReason(voterId, authorId, REASON_REPEAT_UPVOTE)) return;
    suspicions.save(new VoteAbuseSuspicion(voterId, authorId, REASON_REPEAT_UPVOTE, (int) distinct));
    CommunityReputationSuspectedEvent event = new CommunityReputationSuspectedEvent(
        UUID.randomUUID(), Instant.now(), voterId, authorId, REASON_REPEAT_UPVOTE, (int) distinct);
    OutboxEntry entry = new OutboxEntry();
    entry.setAggregateType("community_reputation");
    entry.setAggregateId(voterId + ":" + authorId + ":" + REASON_REPEAT_UPVOTE);
    entry.setEventType(CommunityReputationSuspectedEvent.EVENT_TYPE);
    entry.setPayload(jsonMapper.writeValueAsString(event));
    entry.setCreatedAt(Instant.now());
    outbox.save(entry);
  }
}

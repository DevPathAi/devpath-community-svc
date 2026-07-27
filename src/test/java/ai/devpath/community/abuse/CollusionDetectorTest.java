package ai.devpath.community.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import ai.devpath.community.outbox.OutboxRepository;
import ai.devpath.community.reputation.ReputationService;
import ai.devpath.shared.event.CommunityReputationSuspectedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollusionDetectorTest {
  @Autowired CollusionDetector detector;
  @Autowired ReputationService reputation;
  @Autowired VoteAbuseSuspicionRepository suspicions;
  @Autowired OutboxRepository outbox;

  /** POST upvote(+5)로 5건 생성 — 답변 upvote(+10)는 일일 +40 상한에 4건에서 막힌다. */
  private void upvotePosts(long voter, long author, int count) {
    for (int i = 0; i < count; i++) {
      reputation.applyVote(author, voter, "POST", 90_000 + i, 0, 1, List.of());
    }
  }

  @Test
  void recordsSuspicionAtThreshold() {
    long voter = 80001, author = 80002;
    upvotePosts(voter, author, 5); // 5 distinct POST upvotes V->A
    long before = outbox.count();

    detector.checkOnUpvote(voter, author, "POST", 90_004);

    assertThat(suspicions.existsByActorIdAndTargetUserIdAndReason(voter, author, "REPEAT_UPVOTE")).isTrue();
    assertThat(outbox.count()).isEqualTo(before + 1);
    assertThat(outbox.findAll().stream().anyMatch(e ->
        CommunityReputationSuspectedEvent.EVENT_TYPE.equals(e.getEventType())
            && e.getPayload().contains("\"reason\":\"REPEAT_UPVOTE\""))).isTrue();
  }

  @Test
  void noSuspicionBelowThreshold() {
    long voter = 80003, author = 80004;
    upvotePosts(voter, author, 4); // 4 < 5
    detector.checkOnUpvote(voter, author, "POST", 90_003);
    assertThat(suspicions.existsByActorIdAndTargetUserIdAndReason(voter, author, "REPEAT_UPVOTE")).isFalse();
  }

  @Test
  void idempotentOnRepeatedDetection() {
    long voter = 80005, author = 80006;
    upvotePosts(voter, author, 6);
    detector.checkOnUpvote(voter, author, "POST", 90_005);
    long afterFirst = outbox.count();
    detector.checkOnUpvote(voter, author, "POST", 90_005); // 재탐지 → no-op
    assertThat(suspicions.count()).isEqualTo(1);
    assertThat(outbox.count()).isEqualTo(afterFirst);
  }
}

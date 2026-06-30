package ai.devpath.community.reputation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReputationServiceTest {
  @Autowired ReputationService svc;
  @Autowired UserReputationRepository reputations;
  @Autowired UserTagReputationRepository tagReputations;

  @Test
  void answerUpvoteGrantsTenToAuthorAndTags() {
    long author = 1001, voter = 1002, answerId = 5001;
    svc.applyVote(author, voter, "ANSWER", answerId, 0, 1, List.of(7L, 8L));

    assertThat(reputations.findByUserId(author).orElseThrow().getTotal()).isEqualTo(10);
    assertThat(tagReputations.findByUserIdAndTagId(author, 7L).orElseThrow().getScore()).isEqualTo(10);
    assertThat(tagReputations.findByUserIdAndTagId(author, 8L).orElseThrow().getScore()).isEqualTo(10);
  }

  @Test
  void questionUpvoteGrantsFive() {
    svc.applyVote(2001, 2002, "POST", 6001, 0, 1, List.of());
    assertThat(reputations.findByUserId(2001L).orElseThrow().getTotal()).isEqualTo(5);
  }

  @Test
  void changingUpvoteToDownvoteReversesAndAppliesPenalty() {
    long author = 3001, voter = 3002, answerId = 7001;
    svc.applyVote(author, voter, "ANSWER", answerId, 0, 1, List.of(9L));   // +10
    svc.applyVote(author, voter, "ANSWER", answerId, 1, -1, List.of(9L));  // -10 역산, -2 받음
    assertThat(reputations.findByUserId(author).orElseThrow().getTotal()).isEqualTo(-2);
    assertThat(tagReputations.findByUserIdAndTagId(author, 9L).orElseThrow().getScore()).isEqualTo(-2);
    assertThat(reputations.findByUserId(voter).orElseThrow().getTotal()).isEqualTo(-1); // 행사 비용
  }

  @Test
  void dailyUpvoteGainCappedAtForty() {
    long author = 4001;
    // 답변 upvote +10 × 5 = 50 → 40으로 클램프(서로 다른 답변/투표자).
    for (int i = 0; i < 5; i++) {
      svc.applyVote(author, 5000 + i, "ANSWER", 8000 + i, 0, 1, List.of());
    }
    assertThat(reputations.findByUserId(author).orElseThrow().getTotal()).isEqualTo(40);
  }

  @Test
  void acceptanceExemptFromDailyCap() {
    long author = 4101;
    for (int i = 0; i < 5; i++) svc.applyVote(author, 5100 + i, "ANSWER", 8100 + i, 0, 1, List.of()); // 40
    svc.applyAcceptance(author, 4102, "ANSWER", 8200, List.of()); // +15 (상한 무관)
    assertThat(reputations.findByUserId(author).orElseThrow().getTotal()).isEqualTo(55);
  }
}

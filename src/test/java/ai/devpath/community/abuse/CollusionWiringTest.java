package ai.devpath.community.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import ai.devpath.community.post.QuestionService;
import ai.devpath.community.post.VoteService;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import ai.devpath.community.reputation.ReputationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollusionWiringTest {
  @Autowired QuestionService questionService;
  @Autowired VoteService voteService;
  @Autowired ReputationService reputation;
  @Autowired VoteAbuseSuspicionRepository suspicions;

  @Test
  void votePostTriggersCollusionDetectionAtThreshold() {
    long author = 81001, voter = 81002;
    // voter 평판 15 사전 부여(게이트 통과) — applyAcceptance(answerAuthorId, questionAuthorId, ...)는
    // answerAuthorId에게 ACCEPTED(+15)를 부여한다.
    reputation.applyAcceptance(voter, 99990L, "ANSWER", 1L, List.of());

    // author가 쓴 서로 다른 질문 5개를 voter가 upvote → COLLUSION_UPVOTE_THRESHOLD(5) 도달
    for (int i = 0; i < 5; i++) {
      var q = questionService.create(author, new CreateQuestionRequest("t" + i, "b", List.of()));
      voteService.votePost(voter, q.id(), 1);
    }

    assertThat(suspicions.existsByActorIdAndTargetUserIdAndReason(voter, author, "REPEAT_UPVOTE")).isTrue();
  }
}

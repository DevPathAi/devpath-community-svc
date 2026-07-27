package ai.devpath.community.badge;

import static org.assertj.core.api.Assertions.assertThat;

import ai.devpath.community.post.AnswerService;
import ai.devpath.community.post.QuestionService;
import ai.devpath.community.post.VoteService;
import ai.devpath.community.post.dto.CreateAnswerRequest;
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
class BadgeTriggerTest {
  @Autowired QuestionService questionService;
  @Autowired AnswerService answerService;
  @Autowired UserBadgeRepository userBadges;
  @Autowired BadgeRepository badges;
  @Autowired VoteService voteService;
  @Autowired ReputationService reputation;

  private boolean has(long userId, String code) {
    long badgeId = badges.findByCode(code).orElseThrow().getId();
    return userBadges.existsByUserIdAndBadgeId(userId, badgeId);
  }

  @Test
  void firstQuestionAwardsBadge() {
    long asker = 71001;
    questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    assertThat(has(asker, "FIRST_QUESTION")).isTrue();
  }

  @Test
  void firstAnswerAwardsBadge() {
    long asker = 71002, answerer = 71003;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    assertThat(has(answerer, "FIRST_ANSWER")).isTrue();
  }

  @Test
  void postReachingPlusOneAwardsStudentToAuthor() {
    long asker = 72001, voter = 72002;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    reputation.applyAcceptance(voter, 99990L, "ANSWER", 1L, List.of()); // voter 평판 15(게이트 통과)
    voteService.votePost(voter, q.id(), 1);
    assertThat(has(asker, "STUDENT")).isTrue();
  }

  @Test
  void answerReachingPlusOneAwardsTeacherToAuthor() {
    long asker = 72003, answerer = 72004, voter = 72005;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    voteService.voteAnswer(voter, a.id(), 1); // 답변 upvote는 게이트 없음
    assertThat(has(answerer, "TEACHER")).isTrue();
    assertThat(has(asker, "STUDENT")).isFalse();
  }

  @Test
  void castingDownvoteAwardsCriticToVoter() {
    long asker = 72006, voter = 72008;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    voteService.votePost(voter, q.id(), -1); // 질문 downvote는 레벨 게이트 없음 → CRITIC
    assertThat(has(voter, "CRITIC")).isTrue();
  }

  @Test
  void reachingReputation15AwardsPhilanthropist() {
    long answerer = 73001, asker = 73002;
    var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
    var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
    answerService.accept(asker, a.id()); // 답변 채택 → answerer +15
    assertThat(has(answerer, "PHILANTHROPIST")).isTrue();
  }
}

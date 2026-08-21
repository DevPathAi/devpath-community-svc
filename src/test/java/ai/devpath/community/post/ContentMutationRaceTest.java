package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.devpath.community.post.dto.CreateAnswerRequest;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import ai.devpath.community.reputation.UserReputationRepository;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 변경 경로의 동시성 계약. 각 테스트는 <b>테스트가 첫 트랜잭션을 쥔 채</b> 두 번째 요청을
 * 워커 스레드로 던져, 확률적인 경쟁을 결정적으로 만든다.
 *
 * <p>★단언 두 개가 서로 다른 일을 한다★
 * <ul>
 *   <li>{@code get(500ms)} 가 타임아웃한다 = <b>인터리빙이 실제로 일어났다는 증거</b>.
 *       없으면 두 번째 요청이 커밋 이후에 통째로 실행돼도 테스트가 통과한다.
 *   <li>커밋 뒤의 결과(404 · 평판 0) = <b>락이 있어야만 성립하는 것</b>.
 * </ul>
 * "막혔다" 는 락이 없어도 참이다 — 두 번째 요청도 자기 UPDATE 에서 첫 트랜잭션의 미커밋
 * UPDATE 에 막힌다. 그래서 그것만으로는 판별력이 0 이다.
 *
 * <p>★{@code @Transactional} 을 붙이지 않는다★ — 붙이면 워커 스레드가 커밋된 데이터를 못 본다.
 * 그래서 정리를 {@link #isolate()} 가 직접 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContentMutationRaceTest {

  /** 이 테스트가 독점하는 사용자 id 공간. 정리도 정확히 이 범위로만 한다. */
  private static final long LO = 9500L;
  private static final long HI = 9599L;

  @Autowired PlatformTransactionManager txm;
  @Autowired JdbcTemplate jdbc;
  @Autowired QuestionService questionService;
  @Autowired AnswerService answerService;
  @Autowired PostService postService;
  @Autowired VoteService voteService;
  @Autowired ContentAdminService contentAdmin;
  @Autowired UserReputationRepository reputations;

  TransactionTemplate tx;
  ExecutorService pool;

  @BeforeEach
  void isolate() {
    tx = new TransactionTemplate(txm);
    pool = Executors.newSingleThreadExecutor();
    jdbc.update("DELETE FROM community_votes WHERE user_id BETWEEN ? AND ?", LO, HI);
    jdbc.update("DELETE FROM reputation_events WHERE user_id BETWEEN ? AND ?"
        + " OR actor_id BETWEEN ? AND ?", LO, HI, LO, HI);
    jdbc.update("DELETE FROM user_tag_reputation WHERE user_id BETWEEN ? AND ?", LO, HI);
    jdbc.update("DELETE FROM user_reputation WHERE user_id BETWEEN ? AND ?", LO, HI);
    jdbc.update("DELETE FROM user_badges WHERE user_id BETWEEN ? AND ?", LO, HI);
    jdbc.update("DELETE FROM community_comments WHERE author_id BETWEEN ? AND ?"
        + " OR post_id IN (SELECT id FROM community_posts WHERE author_id BETWEEN ? AND ?)",
        LO, HI, LO, HI);
    jdbc.update("DELETE FROM community_answers WHERE author_id BETWEEN ? AND ?"
        + " OR question_id IN (SELECT id FROM community_posts WHERE author_id BETWEEN ? AND ?)",
        LO, HI, LO, HI);
    jdbc.update("DELETE FROM community_post_tags WHERE post_id IN"
        + " (SELECT id FROM community_posts WHERE author_id BETWEEN ? AND ?)", LO, HI);
    jdbc.update("DELETE FROM community_questions WHERE post_id IN"
        + " (SELECT id FROM community_posts WHERE author_id BETWEEN ? AND ?)", LO, HI);
    jdbc.update("DELETE FROM community_posts WHERE author_id BETWEEN ? AND ?", LO, HI);
  }

  @AfterEach
  void stopPool() {
    pool.shutdownNow();
  }

  int repOf(long userId) {
    return reputations.findByUserId(userId).map(r -> r.getTotal()).orElse(0);
  }

  /** 워커 스레드에서 자기 트랜잭션으로 실행한다. */
  Future<?> inAnotherTransaction(Runnable body) {
    return pool.submit(() -> tx.execute(st -> {
      body.run();
      return null;
    }));
  }

  /** 워커가 예외 없이 끝났음을 단언하고 결과를 돌려준다. */
  Object awaitSuccess(Future<?> f) {
    try {
      return f.get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new AssertionError("워커가 정상 종료해야 한다", e);
    }
  }

  /** 아직 진행 중이어야 한다 = 인터리빙이 실제로 일어났다는 증거. */
  void assertStillInFlight(Future<?> f) {
    assertThatThrownBy(() -> f.get(500, TimeUnit.MILLISECONDS))
        .as("인터리빙 증거: 두 번째 요청이 아직 진행 중이어야 한다")
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  void inFlightUpvoteCannotLandOnAnAnswerThatWasJustHidden() {
    long asker = 9501, answerer = 9502, firstVoter = 9503, raceVoter = 9504;
    long answerId = tx.execute(st -> {
      var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
      return answerService.add(answerer, q.id(), new CreateAnswerRequest("ans")).id();
    });

    // 대조군: 내려가기 전 upvote 는 실제로 평판을 올린다. 이게 없으면 아래 "0 유지" 가
    // 락 때문인지 애초에 평판이 안 붙은 것인지 구분할 수 없다.
    tx.executeWithoutResult(st -> voteService.voteAnswer(firstVoter, answerId, 1));
    assertThat(repOf(answerer)).isEqualTo(10);

    Future<?>[] vote = new Future<?>[1];
    tx.executeWithoutResult(st -> {
      contentAdmin.hideAnswer(answerId);        // 락 획득 + 평판 회수, 아직 커밋 전
      vote[0] = inAnotherTransaction(() -> voteService.voteAnswer(raceVoter, answerId, 1));
      assertStillInFlight(vote[0]);
    });                                          // 커밋 → 해제

    assertThatThrownBy(() -> vote[0].get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(NotFoundException.class);
    assertThat(repOf(answerer)).as("락이 없으면 여기가 10 이 된다").isZero();
  }

  @Test
  void acceptCannotLandOnAnAnswerThatIsBeingDeleted() {
    long asker = 9511, answerer = 9512;
    long[] ids = tx.execute(st -> {
      var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
      var a = answerService.add(answerer, q.id(), new CreateAnswerRequest("ans"));
      return new long[] {q.id(), a.id()};
    });
    long questionId = ids[0];
    long answerId = ids[1];

    Future<?>[] accept = new Future<?>[1];
    tx.executeWithoutResult(st -> {
      answerService.delete(answerer, answerId);   // 락 획득 + DELETED, 아직 커밋 전
      accept[0] = inAnotherTransaction(() -> answerService.accept(asker, answerId));
      assertStillInFlight(accept[0]);
    });                                            // 커밋 → 해제

    assertThatThrownBy(() -> accept[0].get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(NotFoundException.class);
    assertThat(questionService.detail(questionId).solved())
        .as("락이 없으면 solved 가 삭제된 답변을 가리킨다").isFalse();
    assertThat(repOf(answerer)).as("락이 없으면 채택 보상 +15 가 나간다").isZero();
  }

  @Test
  void sameUserVotingTwiceConcurrentlyDoesNotViolateTheVoteUniqueConstraint() {
    long asker = 9521, answerer = 9522, voter = 9523;
    long answerId = tx.execute(st -> {
      var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
      return answerService.add(answerer, q.id(), new CreateAnswerRequest("ans")).id();
    });

    Future<?>[] second = new Future<?>[1];
    tx.executeWithoutResult(st -> {
      voteService.voteAnswer(voter, answerId, 1);   // 표를 넣고 아직 커밋 전
      second[0] = inAnotherTransaction(() -> voteService.voteAnswer(voter, answerId, 1));
      assertStillInFlight(second[0]);
    });                                              // 커밋 → 해제

    // 예외 없이 끝나야 한다. 락이 없으면 두 요청이 각자 "표가 없다" 고 보고 둘 다 insert 해
    // uq_community_votes 를 위반하고, @Transactional 안이라 rollback-only 로 번진다.
    awaitSuccess(second[0]);

    Integer votes = jdbc.queryForObject(
        "SELECT count(*) FROM community_votes WHERE user_id = ? AND target_type = 'ANSWER'"
            + " AND target_id = ?", Integer.class, voter, answerId);
    assertThat(votes).as("표는 한 행이어야 한다").isEqualTo(1);
    assertThat(repOf(answerer)).as("평판은 한 번만 붙는다").isEqualTo(10);
  }

  @Test
  void inFlightVoteCannotLandOnAPostThatWasJustHidden() {
    long author = 9531, firstVoter = 9532, raceVoter = 9533;
    long postId = tx.execute(st ->
        questionService.create(author, new CreateQuestionRequest("t", "b", List.of())).id());

    // 대조군: 내려가기 전 downvote 는 실제로 평판을 내린다(글 downvote 는 평판 게이트가 없다).
    tx.executeWithoutResult(st -> voteService.votePost(firstVoter, postId, -1));
    assertThat(repOf(author)).isEqualTo(-2);

    Future<?>[] vote = new Future<?>[1];
    tx.executeWithoutResult(st -> {
      contentAdmin.hidePost(postId);            // 락 획득 + 평판 회수, 아직 커밋 전
      vote[0] = inAnotherTransaction(() -> voteService.votePost(raceVoter, postId, -1));
      assertStillInFlight(vote[0]);
    });                                          // 커밋 → 해제

    assertThatThrownBy(() -> vote[0].get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(NotFoundException.class);
    assertThat(repOf(author)).as("락이 없으면 -2 가 다시 붙는다").isZero();
  }
}

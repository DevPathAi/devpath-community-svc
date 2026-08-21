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
import org.junit.jupiter.api.Timeout;
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
 *   <li>커밋 뒤의 결과(404 · 평판 0 · 집계) = <b>풀린 쪽이 새 상태를 본다는 증거</b>.
 * </ul>
 *
 * <p>★"쓰기가 알아서 락을 잡아 준다" 는 기대는 커밋 전 어느 시점에도 성립하지 않는다★ —
 * {@code save()} 는 엔티티를 더티로 만들 뿐 SQL 을 내지 않고, 뒤이은 JPQL 질의가 있어도
 * Hibernate 의 AUTO flush 는 <b>테이블 범위로 판단</b>해 겹치지 않으면 건너뛴다. 실측:
 * 답변 경로는 락이 없으면 <b>막히지도 않고</b> 500ms 안에 성공했다. 명시적
 * {@code FOR UPDATE} 만이 그 시점에 락을 잡는다.
 *
 * <p>★{@code @Transactional} 을 붙이지 않는다★ — 붙이면 워커 스레드가 커밋된 데이터를 못 본다.
 * 그래서 정리를 {@link #isolate()} 가 직접 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Timeout(120)
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
  @Autowired CommentService commentService;
  @Autowired ContentAdminService contentAdmin;
  @Autowired UserReputationRepository reputations;

  TransactionTemplate tx;
  ExecutorService pool;

  /**
   * ★워커는 반드시 데몬이어야 한다★ — JDBC 소켓 읽기에서 막힌 스레드는
   * {@code shutdownNow()} 의 인터럽트에 반응하지 않는다. 비데몬이면 그 스레드 하나 때문에
   * <b>테스트 JVM 이 종료되지 못하고</b> Gradle 이 영원히 기다린다(CI 에서 53분 무출력으로 겪음).
   * 데몬이면 최악의 경우에도 JVM 이 빠져나온다.
   */
  private static ExecutorService daemonPool() {
    return Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "content-race-worker");
      t.setDaemon(true);
      return t;
    });
  }

  @BeforeEach
  void isolate() {
    tx = new TransactionTemplate(txm);
    pool = daemonPool();
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

  @Test
  void concurrentVotesFromTwoUsersDoNotLoseTheAggregate() {
    long author = 9541, voterA = 9542, voterB = 9543;
    long postId = tx.execute(st ->
        questionService.create(author, new CreateQuestionRequest("t", "b", List.of())).id());

    Future<?>[] b = new Future<?>[1];
    tx.executeWithoutResult(st -> {
      voteService.votePost(voterA, postId, -1);   // A 의 표, 아직 커밋 전
      b[0] = inAnotherTransaction(() -> voteService.votePost(voterB, postId, -1));
      assertStillInFlight(b[0]);
    });                                            // 커밋 → 해제
    awaitSuccess(b[0]);

    Integer stored = jdbc.queryForObject(
        "SELECT downvote_count FROM community_posts WHERE id = ?", Integer.class, postId);
    assertThat(stored).as("락이 없으면 B 가 A 를 못 세어 1 이 저장된다").isEqualTo(2);
  }

  /**
   * ★수정은 "본문만 바꾸는" 것이 아니다★ — 엔티티에 {@code @DynamicUpdate} 가 없어 flush 는
   * 전 컬럼 UPDATE 다. 잠그지 않은 수정이 내리기와 겹치면 읽어 둔 stale PUBLISHED 가
   * HIDDEN 을 되돌려 쓴다(모더레이션 우회) + deleted=false 색인 이벤트로 검색에도 부활한다.
   */
  @Test
  void inFlightEditCannotResurrectAHiddenPost() {
    long author = 9561, raceAdminTarget;
    long postId = tx.execute(st ->
        questionService.create(author, new CreateQuestionRequest("t", "b", List.of())).id());
    raceAdminTarget = postId;

    Future<?>[] edit = new Future<?>[1];
    tx.executeWithoutResult(st -> {
      contentAdmin.hidePost(raceAdminTarget);      // 락 획득 + HIDDEN, 아직 커밋 전
      edit[0] = inAnotherTransaction(() -> postService.updatePost(author, raceAdminTarget,
          new ai.devpath.community.post.dto.UpdatePostRequest("고친제목", "고친본문")));
      assertStillInFlight(edit[0]);
    });                                             // 커밋 → 해제

    assertThatThrownBy(() -> edit[0].get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(NotFoundException.class);
    String status = jdbc.queryForObject(
        "SELECT status FROM community_posts WHERE id = ?", String.class, postId);
    assertThat(status).as("락이 없으면 stale PUBLISHED 가 HIDDEN 을 되돌려 쓴다")
        .isEqualTo("HIDDEN");
  }

  @Test
  void inFlightCommentEditCannotResurrectAHiddenComment() {
    long author = 9571, commenter = 9572;
    long[] ids = tx.execute(st -> {
      var q = questionService.create(author, new CreateQuestionRequest("t", "b", List.of()));
      var cm = commentService.addComment(commenter, q.id(),
          new ai.devpath.community.post.dto.CreateCommentRequest("원댓글"));
      return new long[] {q.id(), cm.id()};
    });
    long commentId = ids[1];

    Future<?>[] edit = new Future<?>[1];
    tx.executeWithoutResult(st -> {
      contentAdmin.hideComment(commentId);
      edit[0] = inAnotherTransaction(() -> commentService.update(commenter, commentId,
          new ai.devpath.community.post.dto.UpdateBodyRequest("고친댓글")));
      assertStillInFlight(edit[0]);
    });

    assertThatThrownBy(() -> edit[0].get(10, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(NotFoundException.class);
    String status = jdbc.queryForObject(
        "SELECT status FROM community_comments WHERE id = ?", String.class, commentId);
    assertThat(status).isEqualTo("HIDDEN");
  }

  /**
   * ★답변 행 락은 "서로 다른 답변" 의 동시 채택을 직렬화하지 못한다★ — 공유 상태는 질문
   * 행이다. 질문을 잠그지 않으면 둘 다 stale "미채택" 을 보고 진행해 두 답변이 모두
   * accepted 가 되고 보상이 두 번 나간다.
   */
  @Test
  void concurrentAcceptsOfDifferentAnswersRewardOnlyOnce() {
    long asker = 9581, answererA = 9582, answererB = 9583;
    long[] ids = tx.execute(st -> {
      var q = questionService.create(asker, new CreateQuestionRequest("t", "b", List.of()));
      var a = answerService.add(answererA, q.id(), new CreateAnswerRequest("답A"));
      var b = answerService.add(answererB, q.id(), new CreateAnswerRequest("답B"));
      return new long[] {q.id(), a.id(), b.id()};
    });
    long answerA = ids[1];
    long answerB = ids[2];

    Future<?>[] second = new Future<?>[1];
    tx.executeWithoutResult(st -> {
      answerService.accept(asker, answerA);        // 질문 행 락 + 채택, 아직 커밋 전
      second[0] = inAnotherTransaction(() -> answerService.accept(asker, answerB));
      assertStillInFlight(second[0]);
    });                                             // 커밋 → 해제

    assertThatThrownBy(() -> second[0].get(10, TimeUnit.SECONDS))
        .as("두 번째 채택은 이미 채택된 질문임을 보고 물러나야 한다")
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(ai.devpath.community.report.ConflictException.class);
    assertThat(repOf(answererA)).as("먼저 간 채택의 보상만 남는다").isEqualTo(15);
    assertThat(repOf(answererB)).as("락이 없으면 여기도 15 가 된다").isZero();
    Integer acceptedCount = jdbc.queryForObject(
        "SELECT count(*) FROM community_answers WHERE question_id = ? AND is_accepted",
        Integer.class, ids[0]);
    assertThat(acceptedCount).as("accepted 답변은 정확히 하나").isEqualTo(1);
  }

  /**
   * ★콘텐츠 행 락으로는 못 막는 경쟁★ — 같은 작성자의 <b>서로 다른</b> 글에 동시에 투표가
   * 들어오면 서로 다른 행을 잠그므로 둘 다 통과한다. 그런데 둘 다 그 작성자의 평판 행이
   * 없다고 보고 각자 INSERT 한다.
   *
   * <p>{@code ReputationService.addTotal} 이 find-then-save 이기 때문이다. 신규 사용자일수록
   * 잘 맞는다 — 생애 첫 평판 이벤트가 두 콘텐츠에서 동시에 나면 요청이 500 으로 끝난다.
   */
  @Test
  void twoContentsOfTheSameAuthorCanRaceToCreateTheFirstReputationRow() {
    long author = 9551, voterA = 9552, voterB = 9553;
    long[] postIds = tx.execute(st -> new long[] {
        questionService.create(author, new CreateQuestionRequest("t1", "b", List.of())).id(),
        questionService.create(author, new CreateQuestionRequest("t2", "b", List.of())).id()});

    Future<?>[] second = new Future<?>[1];
    tx.executeWithoutResult(st -> {
      voteService.votePost(voterA, postIds[0], -1);   // 작성자 평판 행을 만들며 아직 커밋 전
      second[0] = inAnotherTransaction(() -> voteService.votePost(voterB, postIds[1], -1));
      assertStillInFlight(second[0]);
    });                                                // 커밋 → 해제
    awaitSuccess(second[0]);

    assertThat(repOf(author))
        .as("두 글에서 각각 -2 씩, 합쳐 -4 여야 한다(덮어쓰기가 아니라 누적)")
        .isEqualTo(-4);
  }
}

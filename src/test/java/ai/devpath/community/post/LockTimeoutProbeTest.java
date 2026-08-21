package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.devpath.community.post.dto.CreateAnswerRequest;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ★락 대기는 반드시 유한해야 한다★ — 무한 대기는 커넥션을 쌓아 서비스를 죽인다.
 *
 * <p>이 테스트는 "어떤 수단으로" 유한한지는 묻지 않는다. JPA 힌트가 먹든 커넥션 옵션이 먹든
 * 결과만 본다. 그래서 수단을 바꿔도 같은 테스트가 계약을 지킨다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LockTimeoutProbeTest {

  @Autowired PlatformTransactionManager txm;
  @Autowired CommunityAnswerRepository answers;
  @Autowired QuestionService questionService;
  @Autowired AnswerService answerService;

  @Test
  void lockWaitIsBounded() {
    TransactionTemplate tx = new TransactionTemplate(txm);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      long answerId = tx.execute(st -> {
        var q = questionService.create(9601L, new CreateQuestionRequest("t", "b", List.of()));
        return answerService.add(9602L, q.id(), new CreateAnswerRequest("ans")).id();
      });

      Future<?>[] waiter = new Future<?>[1];
      long[] elapsedMs = new long[1];
      tx.executeWithoutResult(st -> {
        answers.findByIdForUpdate(answerId).orElseThrow();   // 락을 쥐고 놓지 않는다
        long startedAt = System.nanoTime();
        waiter[0] = pool.submit(() ->
            tx.execute(inner -> answers.findByIdForUpdate(answerId).orElseThrow()));
        // 8초는 목표 3초의 두 배 이상이다. 유한하면 그 전에 "예외로" 끝난다.
        // 무한이면 여기서 TimeoutException 이 나고, 그것이 곧 실패다.
        //
        // ★"예외로 끝났다" 만 보면 옳은 이유로 통과했는지 알 수 없다★ — 커넥션 풀 고갈이나
        // 교착 감지도 같은 모양이다. 그래서 사유와 경과 시간을 함께 못박는다.
        assertThatThrownBy(() -> waiter[0].get(8, TimeUnit.SECONDS))
            .as("대기가 유한하면 ExecutionException, 무한하면 TimeoutException 이다")
            .isInstanceOf(ExecutionException.class)
            .rootCause()
            .hasMessageContaining("lock timeout");
        elapsedMs[0] = (System.nanoTime() - startedAt) / 1_000_000L;
      });
      assertThat(waiter[0].isDone()).isTrue();
      assertThat(elapsedMs[0])
          .as("설정한 3초 부근에서 끊겨야 한다(즉시 실패도, 무한도 아니다). 실측: %d ms",
              elapsedMs[0])
          .isBetween(2_000L, 6_000L);
    } finally {
      pool.shutdownNow();
    }
  }
}

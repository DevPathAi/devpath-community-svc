package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ContentRevisionRecorderTest {

  @Autowired ContentRevisionRecorder recorder;
  @Autowired ContentRevisionRepository revisions;

  /**
   * ★이 테스트들은 건수를 단언하므로 스스로 격리해야 한다★ — 남긴 행을 지우지 않으면
   * 첫 실행만 통과하고 재실행부터 누적된 행 때문에 실패한다(2026-08-20 에 실제로 겪었다).
   * CI 는 매번 새 DB 라 드러나지 않고, 로컬에서만 터진다.
   */
  @BeforeEach
  void clearFixtureRevisions() {
    for (long id : new long[] {930001L, 930002L, 930003L}) {
      for (String type : new String[] {"POST", "ANSWER", "COMMENT"}) {
        revisions.deleteAll(revisions.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(type, id));
      }
    }
  }

  @Test
  void recordsPreviousBodyOnlyWhenContentActuallyChanges() {
    long targetId = 930001L;

    assertThat(recorder.record("POST", targetId, "제목1", "본문1", "<p>본문1</p>", 1L)).isTrue();
    assertThat(recorder.record("POST", targetId, "제목2", "본문2", "<p>본문2</p>", 1L)).isTrue();

    assertThat(revisions.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("POST", targetId))
        .hasSize(2);
  }

  @Test
  void doesNotRecordWhenNothingChanged() {
    long targetId = 930002L;

    assertThat(recorder.record("POST", targetId, "같은제목", "같은본문", null, 1L)).isTrue();
    // 직전에 기록한 것과 제목·본문이 같으면 기록하지 않는다.
    assertThat(recorder.record("POST", targetId, "같은제목", "같은본문", null, 1L)).isFalse();

    assertThat(revisions.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("POST", targetId))
        .hasSize(1);
  }

  @Test
  void answerAndCommentRevisionsHaveNoTitle() {
    assertThat(recorder.record("ANSWER", 930003L, null, "답변본문", null, 2L)).isTrue();
    assertThat(revisions.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("ANSWER", 930003L))
        .singleElement()
        .satisfies(r -> {
          assertThat(r.getTitle()).isNull();
          assertThat(r.getBodyMd()).isEqualTo("답변본문");
          assertThat(r.getEditedBy()).isEqualTo(2L);
        });
  }
}

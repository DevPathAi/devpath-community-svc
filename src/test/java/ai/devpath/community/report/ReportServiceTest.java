package ai.devpath.community.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.devpath.community.post.CommunityAnswer;
import ai.devpath.community.post.CommunityAnswerRepository;
import ai.devpath.community.post.CommunityComment;
import ai.devpath.community.post.CommunityCommentRepository;
import ai.devpath.community.post.CommunityPost;
import ai.devpath.community.post.CommunityPostRepository;
import ai.devpath.community.post.CommunityQuestion;
import ai.devpath.community.post.CommunityQuestionRepository;
import ai.devpath.community.post.NotFoundException;
import ai.devpath.shared.error.ApiException;
import ai.devpath.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 실제 postgres(devpath_citest) 대상. 신고 접수의 검증 계약을 고정한다. */
@SpringBootTest
@ActiveProfiles("test")
class ReportServiceTest {

  @Autowired ReportService service;
  @Autowired CommunityReportRepository reports;
  @Autowired CommunityPostRepository posts;
  @Autowired CommunityAnswerRepository answers;
  @Autowired CommunityCommentRepository comments;
  @Autowired CommunityQuestionRepository questions;

  @Test
  void createsReportForPost() {
    CommunityPost p = savePost(100L);

    CommunityReport r = service.create(1L, "POST", p.getId(), "SPAM", "광고글입니다");

    assertEquals("OPEN", r.getStatus());
    assertEquals("POST", r.getTargetType());
    assertEquals(p.getId(), r.getTargetId());
    assertEquals("광고글입니다", r.getReason());
    assertTrue(reports.existsByReporterIdAndTargetTypeAndTargetId(1L, "POST", p.getId()));
  }

  @Test
  void rejectsDuplicateReportWithConflict() {
    CommunityPost p = savePost(100L);
    service.create(2L, "POST", p.getId(), "SPAM", null);

    ApiException e = assertThrows(ConflictException.class,
        () -> service.create(2L, "POST", p.getId(), "ABUSE", null));
    assertEquals(ErrorCode.CONFLICT, e.code());
  }

  @Test
  void rejectsSelfReport() {
    CommunityPost p = savePost(55L);

    ApiException e = assertThrows(ApiException.class,
        () -> service.create(55L, "POST", p.getId(), "SPAM", null));
    assertEquals(ErrorCode.VALIDATION_FAILED, e.code());
  }

  @Test
  void rejectsMissingTarget() {
    assertThrows(NotFoundException.class,
        () -> service.create(1L, "POST", 9_999_000_000L, "SPAM", null));
  }

  @Test
  void rejectsUnknownEnumValues() {
    CommunityPost p = savePost(100L);

    ApiException t = assertThrows(ApiException.class,
        () -> service.create(1L, "PHOTO", p.getId(), "SPAM", null));
    assertEquals(ErrorCode.VALIDATION_FAILED, t.code());

    ApiException c = assertThrows(ApiException.class,
        () -> service.create(1L, "POST", p.getId(), "NONSENSE", null));
    assertEquals(ErrorCode.VALIDATION_FAILED, c.code());
  }

  @Test
  void rejectsReasonOver500Chars() {
    CommunityPost p = savePost(100L);
    String tooLong = "가".repeat(501);

    ApiException e = assertThrows(ApiException.class,
        () -> service.create(1L, "POST", p.getId(), "SPAM", tooLong));
    assertEquals(ErrorCode.VALIDATION_FAILED, e.code());
  }

  @Test
  void createsReportForAnswerAndComment() {
    CommunityPost p = saveQuestionPost(100L);
    CommunityAnswer a = new CommunityAnswer();
    a.setQuestionId(p.getId());
    a.setAuthorId(200L);
    a.setBodyMd("답변 본문");
    a = answers.save(a);
    CommunityComment c = new CommunityComment();
    c.setPostId(p.getId());
    c.setAuthorId(300L);
    c.setBodyMd("댓글 본문");
    c = comments.save(c);

    assertEquals("ANSWER", service.create(1L, "ANSWER", a.getId(), "ABUSE", null).getTargetType());
    assertEquals("COMMENT", service.create(1L, "COMMENT", c.getId(), "ABUSE", null).getTargetType());
  }

  /** AI 시드 답변은 authorId 가 null 이다 — 자기신고 검사에서 NPE 가 나면 안 된다. */
  @Test
  void allowsReportingAiAnswerWithNullAuthor() {
    CommunityPost p = saveQuestionPost(100L);
    CommunityAnswer ai = new CommunityAnswer();
    ai.setQuestionId(p.getId());
    ai.setAuthorId(null);
    ai.setBodyMd("AI 초안");
    ai.setAiGenerated(true);
    ai = answers.save(ai);

    assertEquals("OPEN", service.create(1L, "ANSWER", ai.getId(), "INAPPROPRIATE", null).getStatus());
  }

  private CommunityPost savePost(long authorId) {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(authorId);
    p.setBoardType("FREE");
    p.setTitle("신고 대상 글");
    p.setBodyMd("본문");
    p.setStatus("PUBLISHED");
    return posts.save(p);
  }

  /**
   * 답변을 달려면 질문 행이 먼저 있어야 한다 — {@code community_answers.question_id} 가
   * {@code community_questions} 를 참조하는 FK 이고, 그 테이블은 {@code post_id} 를 PK 로
   * 공유한다. 글만 저장하고 답변을 넣으면 FK 위반이 난다.
   */
  private CommunityPost saveQuestionPost(long authorId) {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(authorId);
    p.setBoardType("QNA");
    p.setTitle("신고 대상 질문");
    p.setBodyMd("본문");
    p.setStatus("PUBLISHED");
    p = posts.save(p);
    CommunityQuestion q = new CommunityQuestion();
    q.setPostId(p.getId());
    questions.save(q);
    return p;
  }
}

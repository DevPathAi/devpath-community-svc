package ai.devpath.community.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.devpath.community.post.CommunityPost;
import ai.devpath.community.post.CommunityPostRepository;
import ai.devpath.community.report.dto.AdminReportResponse;
import ai.devpath.community.report.dto.AdminReportView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 관리자 목록·판정. 이 레포 테스트는 트랜잭션 롤백 없이 실 데이터를 적재하므로 절대 건수를
 * 단언하지 않고 <b>생성 전 스냅샷 + 델타</b>로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminReportServiceTest {

  @Autowired ReportService service;
  @Autowired CommunityPostRepository posts;

  @Test
  void listAssemblesTargetInfo() {
    CommunityPost p = savePost("QNA", "신고당한 질문", "본문 내용입니다");
    service.create(1L, "POST", p.getId(), "SPAM", "광고");

    AdminReportView v = findByTarget(p.getId());

    assertEquals("신고당한 질문", v.targetTitle());
    assertTrue(v.targetExcerpt().contains("본문"));
    assertEquals("/community/" + p.getId(), v.targetPath(), "QNA 는 /community/{id}");
    assertEquals("SPAM", v.category());
    assertEquals("OPEN", v.status());
  }

  @Test
  void freeBoardTargetPathUsesPostRoute() {
    CommunityPost p = savePost("FREE", "자유글", "본문");
    service.create(1L, "POST", p.getId(), "AD", null);

    assertEquals("/community/post/" + p.getId(), findByTarget(p.getId()).targetPath());
  }

  @Test
  void reportCountAggregatesAllReportersRegardlessOfStatus() {
    CommunityPost p = savePost("FREE", "여러 명이 신고한 글", "본문");
    service.create(11L, "POST", p.getId(), "SPAM", null);
    service.create(12L, "POST", p.getId(), "ABUSE", null);
    CommunityReport third = service.create(13L, "POST", p.getId(), "AD", null);
    service.resolve(third.getId(), 99L, "REJECT"); // 처리된 신고도 세어야 한다

    assertEquals(3, findByTarget(p.getId()).reportCount());
  }

  @Test
  void statusFilterSelectsOnlyMatching() {
    long openBefore = service.list("OPEN", 0, 100).total();
    CommunityPost p = savePost("FREE", "필터 대상", "본문");
    CommunityReport r = service.create(21L, "POST", p.getId(), "SPAM", null);

    assertEquals(openBefore + 1, service.list("OPEN", 0, 100).total());

    service.resolve(r.getId(), 99L, "RESOLVE");

    assertEquals(openBefore, service.list("OPEN", 0, 100).total(),
        "처리 후에는 OPEN 목록에서 빠져야 한다");
  }

  @Test
  void sizeIsClampedTo100() {
    AdminReportResponse res = service.list(null, 0, 9999);
    assertEquals(100, res.size(), "응답 size 는 실제 적용값이어야 한다");
    assertTrue(res.items().size() <= 100);
  }

  @Test
  void resolveRecordsReviewerAndTimestamp() {
    CommunityPost p = savePost("FREE", "처리 대상", "본문");
    CommunityReport r = service.create(31L, "POST", p.getId(), "SPAM", null);

    CommunityReport done = service.resolve(r.getId(), 77L, "RESOLVE");

    assertEquals("RESOLVED", done.getStatus());
    assertEquals(77L, done.getReviewedBy());
    assertNotNull(done.getReviewedAt());
  }

  @Test
  void rejectRecordsRejectedStatus() {
    CommunityPost p = savePost("FREE", "기각 대상", "본문");
    CommunityReport r = service.create(32L, "POST", p.getId(), "SPAM", null);

    assertEquals("REJECTED", service.resolve(r.getId(), 77L, "REJECT").getStatus());
  }

  @Test
  void resolvingTwiceIsConflict() {
    CommunityPost p = savePost("FREE", "중복 처리", "본문");
    CommunityReport r = service.create(33L, "POST", p.getId(), "SPAM", null);
    service.resolve(r.getId(), 77L, "RESOLVE");

    assertThrows(ConflictException.class, () -> service.resolve(r.getId(), 77L, "REJECT"));
  }

  @Test
  void unknownActionIsRejected() {
    CommunityPost p = savePost("FREE", "잘못된 액션", "본문");
    CommunityReport r = service.create(34L, "POST", p.getId(), "SPAM", null);

    assertThrows(InvalidReportException.class, () -> service.resolve(r.getId(), 77L, "DELETE"));
  }

  @Test
  void missingTargetLeavesTitleNull() {
    CommunityPost p = savePost("FREE", "곧 사라질 글", "본문");
    CommunityReport r = service.create(41L, "POST", p.getId(), "SPAM", null);
    posts.deleteById(p.getId());

    AdminReportView v = service.list(null, 0, 100).items().stream()
        .filter(i -> i.id() == r.getId()).findFirst().orElseThrow();

    assertNull(v.targetTitle(), "삭제된 대상은 제목이 null 이어야 한다");
    assertNull(v.targetPath(), "이동 링크도 없어야 한다");
  }

  private AdminReportView findByTarget(long targetId) {
    return service.list(null, 0, 100).items().stream()
        .filter(i -> i.targetId() == targetId).findFirst().orElseThrow();
  }

  private CommunityPost savePost(String board, String title, String body) {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(500L);
    p.setBoardType(board);
    p.setTitle(title);
    p.setBodyMd(body);
    p.setStatus("PUBLISHED");
    return posts.save(p);
  }
}

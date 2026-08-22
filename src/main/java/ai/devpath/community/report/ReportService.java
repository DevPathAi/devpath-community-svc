package ai.devpath.community.report;

import ai.devpath.community.post.CommunityAnswer;
import ai.devpath.community.post.CommunityAnswerRepository;
import ai.devpath.community.post.CommunityComment;
import ai.devpath.community.post.CommunityCommentRepository;
import ai.devpath.community.post.CommunityPost;
import ai.devpath.community.post.CommunityPostRepository;
import ai.devpath.community.post.Excerpts;
import ai.devpath.community.post.NotFoundException;
import ai.devpath.community.report.dto.AdminReportResponse;
import ai.devpath.community.report.dto.AdminReportView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

  private static final int REASON_MAX = 500;
  private static final int SIZE_MAX = 100;
  private static final int EXCERPT_LEN = 140;

  private final CommunityReportRepository reports;
  private final CommunityPostRepository posts;
  private final CommunityAnswerRepository answers;
  private final CommunityCommentRepository comments;

  public ReportService(CommunityReportRepository reports, CommunityPostRepository posts,
      CommunityAnswerRepository answers, CommunityCommentRepository comments) {
    this.reports = reports;
    this.posts = posts;
    this.answers = answers;
    this.comments = comments;
  }

  /**
   * 신고를 접수한다. 대상 존재·자기신고·중복을 <b>이 시점에</b> 검증한다 — 미루면 관리자
   * 목록에 유령 신고(삭제된 대상, 자기 신고)가 쌓여 목록 자체를 신뢰할 수 없게 된다.
   *
   * <p>{@code @Transactional} 을 붙이지 않는다. 저장이 단일 insert 라 필요 없고, 트랜잭션
   * 안에서 {@link DataIntegrityViolationException} 을 잡으면 rollback-only 로 마킹돼
   * 커밋 시점에 다시 터진다(이 프로젝트의 광고 기능에서 겪은 트랩).
   */
  public CommunityReport create(long reporterId, String targetType, Long targetId,
      String category, String reason) {
    ReportTargetType type = parseTargetType(targetType);
    ReportCategory cat = parseCategory(category);
    if (reason != null && reason.length() > REASON_MAX) {
      throw new InvalidReportException("사유는 " + REASON_MAX + "자를 넘을 수 없습니다.");
    }

    Long authorId = targetAuthorId(type, targetId);
    if (authorId != null && authorId == reporterId) {
      throw new InvalidReportException("본인이 작성한 콘텐츠는 신고할 수 없습니다.");
    }
    if (reports.existsByReporterIdAndTargetTypeAndTargetId(reporterId, type.name(), targetId)) {
      throw new ConflictException("이미 신고한 콘텐츠입니다.");
    }

    CommunityReport r = new CommunityReport();
    r.setReporterId(reporterId);
    r.setTargetType(type.name());
    r.setTargetId(targetId);
    r.setCategory(cat.name());
    r.setReason(reason);
    r.setStatus(ReportStatus.OPEN.name());
    try {
      return reports.save(r);
    } catch (DataIntegrityViolationException e) {
      // exists 검사와 save 사이의 경쟁. UNIQUE 제약이 최종 방어선이다.
      throw new ConflictException("이미 신고한 콘텐츠입니다.");
    }
  }

  /**
   * 대상 작성자 id. 대상이 없으면 404, 있는데 작성자가 없으면 null 을 돌려준다(AI 시드 답변).
   *
   * <p>{@code map(getter).orElseThrow(...)} 로 쓰면 <b>작성자 null 을 "대상 없음"으로 오인해
   * 404</b> 를 던진다. 반드시 먼저 orElseThrow 하고 그다음 getter 를 부른다.
   */
  private Long targetAuthorId(ReportTargetType type, Long targetId) {
    return switch (type) {
      case POST -> posts.findById(targetId)
          .filter(p -> ai.devpath.community.post.ContentStatus.PUBLISHED.equals(p.getStatus()))
          .orElseThrow(() -> new NotFoundException("신고 대상 글을 찾을 수 없습니다."))
          .getAuthorId();
      case ANSWER -> answers.findById(targetId)
          .filter(a -> ai.devpath.community.post.ContentStatus.PUBLISHED.equals(a.getStatus()))
          .orElseThrow(() -> new NotFoundException("신고 대상 답변을 찾을 수 없습니다."))
          .getAuthorId();
      case COMMENT -> comments.findById(targetId)
          .filter(c -> ai.devpath.community.post.ContentStatus.PUBLISHED.equals(c.getStatus()))
          .orElseThrow(() -> new NotFoundException("신고 대상 댓글을 찾을 수 없습니다."))
          .getAuthorId();
    };
  }

  private ReportTargetType parseTargetType(String raw) {
    try {
      return ReportTargetType.valueOf(raw);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidReportException("신고 대상 종류가 올바르지 않습니다: " + raw);
    }
  }

  private ReportCategory parseCategory(String raw) {
    try {
      return ReportCategory.valueOf(raw);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidReportException("신고 사유가 올바르지 않습니다: " + raw);
    }
  }

  // ───────────────────────────── 관리자 ─────────────────────────────

  /**
   * 관리자 목록. {@code status} 가 null 이면 전체. size 는 100 으로 클램프한다(검색 API 와
   * 동일 규칙) — 응답의 {@code size} 는 요청값이 아니라 <b>실제 적용값</b>이다.
   */
  public AdminReportResponse list(String status, int page, int size) {
    int p = Math.max(page, 0);
    int s = Math.min(Math.max(size, 1), SIZE_MAX);
    List<AdminReportView> items = reports.findPage(status, PageRequest.of(p, s)).stream()
        .map(this::toAdminView)
        .collect(Collectors.toList());
    return new AdminReportResponse(items, reports.countFiltered(status), p, s);
  }

  /** 신고와 대상 콘텐츠를 함께 조립한다. 같은 DB 에 있어 교차 서비스 호출이 필요 없다. */
  private AdminReportView toAdminView(CommunityReport r) {
    ReportTargetType type = ReportTargetType.valueOf(r.getTargetType());
    Optional<CommunityAnswer> answer = type == ReportTargetType.ANSWER
        ? answers.findById(r.getTargetId()) : Optional.empty();
    Optional<CommunityComment> comment = type == ReportTargetType.COMMENT
        ? comments.findById(r.getTargetId()) : Optional.empty();
    // 답변·댓글은 부모 글을 찾아 그 경로를 준다.
    Optional<CommunityPost> parent = switch (type) {
      case POST -> posts.findById(r.getTargetId());
      case ANSWER -> answer.flatMap(a -> posts.findById(a.getQuestionId()));
      case COMMENT -> comment.flatMap(c -> posts.findById(c.getPostId()));
    };
    String excerpt = switch (type) {
      case POST -> parent.map(x -> Excerpts.from(x.getBodyMd(), EXCERPT_LEN)).orElse(null);
      case ANSWER -> answer.map(a -> Excerpts.from(a.getBodyMd(), EXCERPT_LEN)).orElse(null);
      case COMMENT -> comment.map(c -> Excerpts.from(c.getBodyMd(), EXCERPT_LEN)).orElse(null);
    };
    Long authorId = switch (type) {
      case POST -> parent.map(CommunityPost::getAuthorId).orElse(null);
      case ANSWER -> answer.map(CommunityAnswer::getAuthorId).orElse(null);
      case COMMENT -> comment.map(CommunityComment::getAuthorId).orElse(null);
    };
    return new AdminReportView(
        r.getId(), r.getTargetType(), r.getTargetId(),
        parent.map(CommunityPost::getTitle).orElse(null),
        excerpt,
        authorId,
        parent.map(this::pathOf).orElse(null),
        r.getReporterId(), r.getCategory(), r.getReason(),
        reports.countByTargetTypeAndTargetId(r.getTargetType(), r.getTargetId()),
        r.getStatus(),
        r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
  }

  /** QNA 는 /community/{id}, 그 외 보드는 /community/post/{id}. 프론트 라우터 규칙과 일치한다. */
  private String pathOf(CommunityPost p) {
    return "QNA".equals(p.getBoardType())
        ? "/community/" + p.getId()
        : "/community/post/" + p.getId();
  }

  /** 판정. 이미 처리된 신고를 다시 처리하면 409 — 두 관리자가 엇갈려 판정하는 것을 막는다. */
  public CommunityReport resolve(long reportId, long reviewerId, String action) {
    ReportStatus next = switch (action == null ? "" : action) {
      case "RESOLVE" -> ReportStatus.RESOLVED;
      case "REJECT" -> ReportStatus.REJECTED;
      default -> throw new InvalidReportException("처리 방식이 올바르지 않습니다: " + action);
    };
    CommunityReport r = reports.findById(reportId)
        .orElseThrow(() -> new NotFoundException("신고를 찾을 수 없습니다."));
    if (!ReportStatus.OPEN.name().equals(r.getStatus())) {
      throw new ConflictException("이미 처리된 신고입니다.");
    }
    r.setStatus(next.name());
    r.setReviewedBy(reviewerId);
    r.setReviewedAt(Instant.now());
    return reports.save(r);
  }
}

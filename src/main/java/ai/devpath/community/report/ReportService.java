package ai.devpath.community.report;

import ai.devpath.community.post.CommunityAnswer;
import ai.devpath.community.post.CommunityAnswerRepository;
import ai.devpath.community.post.CommunityComment;
import ai.devpath.community.post.CommunityCommentRepository;
import ai.devpath.community.post.CommunityPost;
import ai.devpath.community.post.CommunityPostRepository;
import ai.devpath.community.post.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

  private static final int REASON_MAX = 500;

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
          .orElseThrow(() -> new NotFoundException("신고 대상 글을 찾을 수 없습니다."))
          .getAuthorId();
      case ANSWER -> answers.findById(targetId)
          .orElseThrow(() -> new NotFoundException("신고 대상 답변을 찾을 수 없습니다."))
          .getAuthorId();
      case COMMENT -> comments.findById(targetId)
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
}

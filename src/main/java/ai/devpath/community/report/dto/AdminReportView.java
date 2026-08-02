package ai.devpath.community.report.dto;

/**
 * 관리자 목록 항목. 신고와 대상 콘텐츠가 같은 DB 에 있어 한 번에 조립한다.
 *
 * <p>{@code targetPath} 는 서버가 준다 — 프론트가 QNA(/community/{id})와
 * 일반글(/community/post/{id}) 경로 규칙을 중복 구현하지 않게 하기 위해서다.
 * 대상이 삭제됐으면 {@code targetTitle}·{@code targetExcerpt}·{@code targetPath} 가 null 이다.
 */
public record AdminReportView(
    long id,
    String targetType,
    long targetId,
    String targetTitle,
    String targetExcerpt,
    Long targetAuthorId,
    String targetPath,
    long reporterId,
    String category,
    String reason,
    long reportCount,
    String status,
    String createdAt) {}

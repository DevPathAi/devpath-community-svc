package ai.devpath.community.report.dto;

/** 신고 접수 요청. reason 은 선택(최대 500자). */
public record ReportRequest(String targetType, Long targetId, String category, String reason) {}

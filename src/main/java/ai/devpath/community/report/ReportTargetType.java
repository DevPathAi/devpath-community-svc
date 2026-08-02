package ai.devpath.community.report;

/** 신고 대상 종류. DB CHECK 제약(chk_community_reports_target)과 값이 일치해야 한다. */
public enum ReportTargetType {
  POST, ANSWER, COMMENT
}

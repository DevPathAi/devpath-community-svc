package ai.devpath.community.report;

/** 신고 사유. DB CHECK 제약(chk_community_reports_category)과 값이 일치해야 한다. */
public enum ReportCategory {
  SPAM, ABUSE, AD, DUPLICATE, INAPPROPRIATE, ETC
}

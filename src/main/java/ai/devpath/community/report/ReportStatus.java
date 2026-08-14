package ai.devpath.community.report;

/**
 * 신고 처리 상태. REJECTED(기각)를 RESOLVED(처리완료)와 분리해 둔다 — 이번 범위는 판정
 * 기록뿐이므로, 두 갈래 판단이 구분돼 남아야 나중에 조치·제재를 붙일 때 이력이 쓸모를 가진다.
 */
public enum ReportStatus {
  OPEN, RESOLVED, REJECTED
}

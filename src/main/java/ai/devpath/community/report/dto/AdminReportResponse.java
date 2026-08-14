package ai.devpath.community.report.dto;

import java.util.List;

/** 검색 API 와 같은 envelope 형태. size 는 클램프된 실적용값이다. */
public record AdminReportResponse(List<AdminReportView> items, long total, int page, int size) {}

package ai.devpath.community.search.dto;

import java.util.List;

/** {@code GET /community/search} 응답. {@code total}은 ES 관련도 매칭 전체 건수(페이지 크기와 무관). */
public record SearchResponse(List<SearchItemView> items, long total, int page, int size) {}

package ai.devpath.community.search.dto;

/** 검색 결과 1건. {@code highlight}는 본문 매칭 하이라이트(없으면 기존 excerpt로 폴백). */
public record SearchItemView(long id, String boardType, String title, Long authorId,
    boolean solved, int upvoteCount, int replyCount, String excerpt, String highlight) {}

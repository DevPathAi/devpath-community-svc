package ai.devpath.community.post.dto;

import java.time.Instant;

/**
 * 수정 이력 한 건. 최신순으로 낸다.
 *
 * <p>페이지네이션을 두지 않는다 — 한 콘텐츠의 수정 횟수는 실무상 작다.
 */
public record RevisionView(String targetType, long targetId, String title, String bodyMd,
    long editedBy, Instant createdAt) {}

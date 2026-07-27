package ai.devpath.community.badge;

import java.time.Instant;

public record BadgeView(String code, String name, String tier, Instant awardedAt) {}

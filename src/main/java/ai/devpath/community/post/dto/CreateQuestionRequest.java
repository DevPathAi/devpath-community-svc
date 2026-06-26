package ai.devpath.community.post.dto;

import java.util.List;

public record CreateQuestionRequest(String title, String bodyMd, List<String> tags) {}

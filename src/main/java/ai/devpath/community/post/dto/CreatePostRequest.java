package ai.devpath.community.post.dto;
import java.util.List;
public record CreatePostRequest(String boardType, String title, String bodyMd, List<String> tags) {}

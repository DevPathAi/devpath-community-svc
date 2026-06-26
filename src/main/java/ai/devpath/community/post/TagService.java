package ai.devpath.community.post;

import ai.devpath.community.post.dto.TagView;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TagService {
  private final CommunityTagRepository tags;
  public TagService(CommunityTagRepository tags) { this.tags = tags; }

  public List<TagView> autocomplete(String q) {
    if (q == null || q.isBlank()) return List.of();
    return tags.findTop10ByNameStartingWithOrderByPostCountDesc(q).stream()
        .map(t -> new TagView(t.getId(), t.getName(), t.getPostCount()))
        .collect(Collectors.toList());
  }
}

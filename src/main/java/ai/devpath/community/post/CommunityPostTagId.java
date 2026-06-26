package ai.devpath.community.post;

import java.io.Serializable;
import java.util.Objects;

public class CommunityPostTagId implements Serializable {
  private Long postId;
  private Long tagId;
  public CommunityPostTagId() {}
  public CommunityPostTagId(Long postId, Long tagId) { this.postId = postId; this.tagId = tagId; }
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CommunityPostTagId that)) return false;
    return Objects.equals(postId, that.postId) && Objects.equals(tagId, that.tagId);
  }
  @Override public int hashCode() { return Objects.hash(postId, tagId); }
}

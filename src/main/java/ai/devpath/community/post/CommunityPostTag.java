package ai.devpath.community.post;

import jakarta.persistence.*;

@Entity
@Table(name = "community_post_tags")
@IdClass(CommunityPostTagId.class)
public class CommunityPostTag {
  @Id @Column(name = "post_id") private Long postId;
  @Id @Column(name = "tag_id") private Long tagId;
  public CommunityPostTag() {}
  public CommunityPostTag(Long postId, Long tagId) { this.postId = postId; this.tagId = tagId; }
  public Long getPostId() { return postId; }
  public Long getTagId() { return tagId; }
}

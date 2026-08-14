package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CommunityCommentRepositoryTest {

  @Autowired CommunityPostRepository posts;
  @Autowired CommunityCommentRepository comments;

  @Test
  void savesAndListsCommentsByPostInOrder() {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(1L); p.setBoardType("FREE"); p.setTitle("t"); p.setBodyMd("b"); p.setStatus("PUBLISHED");
    p = posts.save(p);

    CommunityComment c1 = new CommunityComment();
    c1.setPostId(p.getId()); c1.setAuthorId(2L); c1.setBodyMd("첫 댓글");
    comments.save(c1);
    CommunityComment c2 = new CommunityComment();
    c2.setPostId(p.getId()); c2.setAuthorId(3L); c2.setBodyMd("둘째 댓글");
    comments.save(c2);

    List<CommunityComment> list = comments.findByPostIdOrderByCreatedAtAsc(p.getId());
    assertThat(list).extracting(CommunityComment::getBodyMd).containsExactly("첫 댓글", "둘째 댓글");
    assertThat(comments.countByPostId(p.getId())).isEqualTo(2);
  }
}

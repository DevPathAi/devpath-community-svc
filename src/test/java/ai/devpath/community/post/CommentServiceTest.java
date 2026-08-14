package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.devpath.community.post.dto.CreateCommentRequest;
import ai.devpath.community.post.dto.CreatePostRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CommentServiceTest {

  @Autowired PostService postService;
  @Autowired CommentService commentService;

  @Test
  void addAndListComments_inOrder() {
    long postId = postService.createPost(20L,
        new CreatePostRequest("FREE", "글", "본문", List.of())).id();
    commentService.addComment(21L, postId, new CreateCommentRequest("댓글1"));
    commentService.addComment(22L, postId, new CreateCommentRequest("댓글2"));

    List<?> list = commentService.listComments(postId);
    assertThat(list).hasSize(2);
    assertThat(postService.postDetail(postId).comments()).hasSize(2);
  }

  @Test
  void addComment_missingPost_throwsNotFound() {
    assertThatThrownBy(() -> commentService.addComment(20L, 999999L, new CreateCommentRequest("x")))
        .isInstanceOf(NotFoundException.class);
  }
}

package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.devpath.community.post.dto.CreatePostRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PostServiceTest {

  @Autowired PostService postService;
  @Autowired CommunityQuestionRepository questions;

  @Test
  void createFreePost_savesWithoutQuestionRow() {
    var view = postService.createPost(10L,
        new CreatePostRequest("FREE", "자유 글", "자유 본문", List.of("잡담")));
    assertThat(view.boardType()).isEqualTo("FREE");
    assertThat(view.title()).isEqualTo("자유 글");
    assertThat(view.comments()).isEmpty();
    assertThat(questions.findById(view.id())).isEmpty(); // Q&A row 없음
  }

  @Test
  void createFeedbackPost_ok() {
    var view = postService.createPost(11L,
        new CreatePostRequest("FEEDBACK", "피드백 요청", "코드 봐주세요", List.of()));
    assertThat(view.boardType()).isEqualTo("FEEDBACK");
  }

  @Test
  void createRejectsQnaAndUnknownBoard() {
    assertThatThrownBy(() -> postService.createPost(12L,
        new CreatePostRequest("QNA", "t", "b", List.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> postService.createPost(12L,
        new CreatePostRequest("STUDY", "t", "b", List.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void postDetail_missingPost_throwsNotFound() {
    assertThatThrownBy(() -> postService.postDetail(999999L))
        .isInstanceOf(NotFoundException.class);
  }
}

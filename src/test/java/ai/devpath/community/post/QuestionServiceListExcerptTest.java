package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import ai.devpath.community.post.dto.CreatePostRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class QuestionServiceListExcerptTest {

  @Autowired PostService postService;
  @Autowired QuestionService questionService;

  @Test
  void listPopulatesExcerptFromBody() {
    postService.createPost(77L,
        new CreatePostRequest("FREE", "요약 테스트", "## 헤더\n**본문** 미리보기 내용", List.of()));

    var view = questionService.list(null, null, null).stream()
        .filter(v -> "요약 테스트".equals(v.title()))
        .findFirst()
        .orElseThrow();

    assertThat(view.excerpt()).isEqualTo("헤더 본문 미리보기 내용");
  }
}

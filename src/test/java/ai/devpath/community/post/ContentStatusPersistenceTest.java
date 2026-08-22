package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ContentStatusPersistenceTest {

  @Autowired CommunityPostRepository posts;
  @Autowired CommunityQuestionRepository questions;
  @Autowired CommunityAnswerRepository answers;
  @Autowired CommunityCommentRepository comments;

  @Test
  void answerAndCommentPersistStatusAndDefaultToPublished() {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(910001L); p.setBoardType("QNA");
    p.setTitle("상태 저장"); p.setBodyMd("본문"); p.setStatus(ContentStatus.PUBLISHED);
    p = posts.save(p);
    CommunityQuestion q = new CommunityQuestion();
    q.setPostId(p.getId());
    questions.save(q);

    CommunityAnswer a = new CommunityAnswer();
    a.setQuestionId(p.getId()); a.setAuthorId(910002L); a.setBodyMd("답변");
    a = answers.save(a);
    assertThat(a.getStatus()).isEqualTo(ContentStatus.PUBLISHED);

    a.setStatus(ContentStatus.DELETED);
    answers.save(a);
    assertThat(answers.findById(a.getId()).orElseThrow().getStatus())
        .isEqualTo(ContentStatus.DELETED);

    CommunityComment c = new CommunityComment();
    c.setPostId(p.getId()); c.setAuthorId(910002L); c.setBodyMd("댓글");
    c = comments.save(c);
    assertThat(c.getStatus()).isEqualTo(ContentStatus.PUBLISHED);

    c.setStatus(ContentStatus.HIDDEN);
    comments.save(c);
    assertThat(comments.findById(c.getId()).orElseThrow().getStatus())
        .isEqualTo(ContentStatus.HIDDEN);
  }
}

package ai.devpath.community.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.test.database.replace=none")
@ActiveProfiles("test")
class CommunityRepositoryTest {

  @Autowired CommunityPostRepository posts;
  @Autowired CommunityQuestionRepository questions;
  @Autowired CommunityAnswerRepository answers;
  @Autowired CommunityVoteRepository votes;
  @Autowired CommunityTagRepository tags;

  @Test
  void savesQuestionPostAnswerVoteTag() {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(1L); p.setBoardType("QNA"); p.setTitle("t"); p.setBodyMd("b"); p.setStatus("PUBLISHED");
    p = posts.save(p);
    assertNotNull(p.getId());
    assertEquals(0, p.getUpvoteCount());

    CommunityQuestion q = new CommunityQuestion();
    q.setPostId(p.getId());
    questions.save(q);
    assertTrue(questions.findById(p.getId()).isPresent());

    CommunityAnswer a = new CommunityAnswer();
    a.setQuestionId(p.getId()); a.setAuthorId(2L); a.setBodyMd("ans");
    a = answers.save(a);
    assertNotNull(a.getId());

    CommunityVote v = new CommunityVote();
    v.setUserId(3L); v.setTargetType("POST"); v.setTargetId(p.getId()); v.setValue((short) 1);
    votes.save(v);

    CommunityTag tag = new CommunityTag();
    tag.setName("jpa-" + System.nanoTime());
    tag = tags.save(tag);
    assertNotNull(tag.getId());
  }
}

package ai.devpath.community.post;

import ai.devpath.community.post.dto.AnswerView;
import ai.devpath.community.post.dto.CreateAnswerRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerService {
  private final CommunityPostRepository posts;
  private final CommunityQuestionRepository questions;
  private final CommunityAnswerRepository answers;

  public AnswerService(CommunityPostRepository posts, CommunityQuestionRepository questions,
      CommunityAnswerRepository answers) {
    this.posts = posts; this.questions = questions; this.answers = answers;
  }

  @Transactional
  public AnswerView add(long userId, long questionId, CreateAnswerRequest req) {
    questions.findById(questionId).orElseThrow(() -> new NotFoundException("question " + questionId));
    CommunityAnswer a = new CommunityAnswer();
    a.setQuestionId(questionId); a.setAuthorId(userId); a.setBodyMd(req.bodyMd());
    a = answers.save(a);
    return new AnswerView(a.getId(), a.getAuthorId(), a.getBodyMd(),
        a.isAiGenerated(), a.isAccepted(), a.getUpvoteCount());
  }

  @Transactional
  public void accept(long userId, long answerId) {
    CommunityAnswer a = answers.findById(answerId)
        .orElseThrow(() -> new NotFoundException("answer " + answerId));
    CommunityQuestion q = questions.findById(a.getQuestionId())
        .orElseThrow(() -> new NotFoundException("question " + a.getQuestionId()));
    CommunityPost p = posts.findById(q.getPostId())
        .orElseThrow(() -> new NotFoundException("post " + q.getPostId()));
    if (p.getAuthorId() == null || p.getAuthorId() != userId) {
      throw new ForbiddenException("only question author can accept");
    }
    a.setAccepted(true);
    answers.save(a);
    q.setSolved(true);
    q.setAcceptedAnswerId(answerId);
    questions.save(q);
  }
}

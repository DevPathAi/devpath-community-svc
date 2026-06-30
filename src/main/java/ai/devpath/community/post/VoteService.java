package ai.devpath.community.post;

import ai.devpath.community.reputation.RepPoints;
import ai.devpath.community.reputation.ReputationService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoteService {
  private final CommunityVoteRepository votes;
  private final CommunityPostRepository posts;
  private final CommunityAnswerRepository answers;
  private final CommunityQuestionRepository questions;
  private final CommunityPostTagRepository postTags;
  private final ReputationService reputation;

  public VoteService(CommunityVoteRepository votes, CommunityPostRepository posts,
      CommunityAnswerRepository answers, CommunityQuestionRepository questions,
      CommunityPostTagRepository postTags, ReputationService reputation) {
    this.votes = votes; this.posts = posts; this.answers = answers;
    this.questions = questions; this.postTags = postTags; this.reputation = reputation;
  }

  @Transactional
  public void votePost(long userId, long postId, int value) {
    validate(value);
    CommunityPost p = posts.findById(postId).orElseThrow(() -> new NotFoundException("post " + postId));
    // 게이트: 질문 upvote는 평판 15 이상
    if (value == 1 && reputation.reputationOf(userId) < RepPoints.LVL_UPVOTE_QUESTION) {
      throw new ForbiddenException("질문 upvote는 평판 " + RepPoints.LVL_UPVOTE_QUESTION + " 이상 필요");
    }
    int oldValue = currentValue(userId, "POST", postId);
    upsert(userId, "POST", postId, (short) value);
    refreshPostCounts(p, postId);
    List<Long> tagIds = postTags.findByPostId(postId).stream().map(CommunityPostTag::getTagId).toList();
    reputation.applyVote(p.getAuthorId(), userId, "POST", postId, oldValue, value, tagIds);
  }

  @Transactional
  public void voteAnswer(long userId, long answerId, int value) {
    validate(value);
    CommunityAnswer a = answers.findById(answerId)
        .orElseThrow(() -> new NotFoundException("answer " + answerId));
    // 게이트: 답변 downvote는 평판 125 이상
    if (value == -1 && reputation.reputationOf(userId) < RepPoints.LVL_DOWNVOTE_ANSWER) {
      throw new ForbiddenException("답변 downvote는 평판 " + RepPoints.LVL_DOWNVOTE_ANSWER + " 이상 필요");
    }
    int oldValue = currentValue(userId, "ANSWER", answerId);
    upsert(userId, "ANSWER", answerId, (short) value);
    a.setUpvoteCount(votes.countByTargetTypeAndTargetIdAndValue("ANSWER", answerId, (short) 1));
    answers.save(a);
    CommunityQuestion q = questions.findById(a.getQuestionId())
        .orElseThrow(() -> new NotFoundException("question " + a.getQuestionId()));
    List<Long> tagIds = postTags.findByPostId(q.getPostId()).stream()
        .map(CommunityPostTag::getTagId).toList();
    reputation.applyVote(a.getAuthorId(), userId, "ANSWER", answerId, oldValue, value, tagIds);
  }

  private int currentValue(long userId, String type, long targetId) {
    return votes.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId)
        .map(v -> (int) v.getValue()).orElse(0);
  }

  private void refreshPostCounts(CommunityPost p, long postId) {
    p.setUpvoteCount(votes.countByTargetTypeAndTargetIdAndValue("POST", postId, (short) 1));
    p.setDownvoteCount(votes.countByTargetTypeAndTargetIdAndValue("POST", postId, (short) -1));
    posts.save(p);
  }

  private void upsert(long userId, String type, long targetId, short value) {
    CommunityVote v = votes.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId)
        .orElseGet(() -> {
          CommunityVote nv = new CommunityVote();
          nv.setUserId(userId); nv.setTargetType(type); nv.setTargetId(targetId);
          return nv;
        });
    v.setValue(value);
    votes.save(v);
  }

  private void validate(int value) {
    if (value != 1 && value != -1) throw new InvalidVoteException("value must be 1 or -1");
  }
}

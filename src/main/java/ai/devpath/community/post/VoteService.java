package ai.devpath.community.post;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoteService {
  private final CommunityVoteRepository votes;
  private final CommunityPostRepository posts;
  private final CommunityAnswerRepository answers;

  public VoteService(CommunityVoteRepository votes, CommunityPostRepository posts,
      CommunityAnswerRepository answers) {
    this.votes = votes; this.posts = posts; this.answers = answers;
  }

  @Transactional
  public void votePost(long userId, long postId, int value) {
    validate(value);
    posts.findById(postId).orElseThrow(() -> new NotFoundException("post " + postId));
    upsert(userId, "POST", postId, (short) value);
    CommunityPost p = posts.findById(postId).orElseThrow();
    p.setUpvoteCount(votes.countByTargetTypeAndTargetIdAndValue("POST", postId, (short) 1));
    p.setDownvoteCount(votes.countByTargetTypeAndTargetIdAndValue("POST", postId, (short) -1));
    posts.save(p);
  }

  @Transactional
  public void voteAnswer(long userId, long answerId, int value) {
    validate(value);
    CommunityAnswer a = answers.findById(answerId)
        .orElseThrow(() -> new NotFoundException("answer " + answerId));
    upsert(userId, "ANSWER", answerId, (short) value);
    a.setUpvoteCount(votes.countByTargetTypeAndTargetIdAndValue("ANSWER", answerId, (short) 1));
    answers.save(a);
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

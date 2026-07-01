package ai.devpath.community.post;

import ai.devpath.community.badge.BadgeCode;
import ai.devpath.community.badge.BadgeService;
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
  private final BadgeService badgeService;

  public VoteService(CommunityVoteRepository votes, CommunityPostRepository posts,
      CommunityAnswerRepository answers, CommunityQuestionRepository questions,
      CommunityPostTagRepository postTags, ReputationService reputation,
      BadgeService badgeService) {
    this.votes = votes; this.posts = posts; this.answers = answers;
    this.questions = questions; this.postTags = postTags; this.reputation = reputation;
    this.badgeService = badgeService;
  }

  @Transactional
  public void votePost(long userId, long postId, int value) {
    validate(value);
    CommunityPost p = posts.findById(postId).orElseThrow(() -> new NotFoundException("post " + postId));
    if (p.getAuthorId() != null && p.getAuthorId() == userId) {
      throw new ForbiddenException("자기 글에는 투표할 수 없습니다");
    }
    // 게이트: 질문 upvote는 평판 15 이상
    if (value == 1 && reputation.reputationOf(userId) < RepPoints.LVL_UPVOTE_QUESTION) {
      throw new ForbiddenException("질문 upvote는 평판 " + RepPoints.LVL_UPVOTE_QUESTION + " 이상 필요");
    }
    int oldValue = currentValue(userId, "POST", postId);
    upsert(userId, "POST", postId, (short) value);
    refreshPostCounts(p, postId);
    List<Long> tagIds = postTags.findByPostId(postId).stream().map(CommunityPostTag::getTagId).toList();
    reputation.applyVote(p.getAuthorId(), userId, "POST", postId, oldValue, value, tagIds);
    // 배지: 순점수 +1 도달 → 글 작성자 STUDENT, downvote 행사 → 투표자 CRITIC
    // 자기 글 투표로도 획득 가능 — 자기투표/sockpuppet 게이팅은 Build 3에서 정합.
    if (p.getUpvoteCount() - p.getDownvoteCount() >= 1) {
      badgeService.award(p.getAuthorId(), BadgeCode.STUDENT, "POST", postId);
    }
    if (value == -1) {
      badgeService.award(userId, BadgeCode.CRITIC, "POST", postId);
    }
    if (reputation.reputationOf(p.getAuthorId()) >= RepPoints.LVL_UPVOTE_QUESTION) {
      badgeService.award(p.getAuthorId(), BadgeCode.PHILANTHROPIST, "POST", postId);
    }
  }

  @Transactional
  public void voteAnswer(long userId, long answerId, int value) {
    validate(value);
    CommunityAnswer a = answers.findById(answerId)
        .orElseThrow(() -> new NotFoundException("answer " + answerId));
    if (a.getAuthorId() != null && a.getAuthorId() == userId) {
      throw new ForbiddenException("자기 답변에는 투표할 수 없습니다");
    }
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
    // 배지: 답변 순점수 +1 도달 → 답변 작성자 TEACHER, downvote 행사 → 투표자 CRITIC
    // 자기 글 투표로도 획득 가능 — 자기투표/sockpuppet 게이팅은 Build 3에서 정합.
    int answerNet = a.getUpvoteCount()
        - votes.countByTargetTypeAndTargetIdAndValue("ANSWER", answerId, (short) -1);
    if (answerNet >= 1) {
      badgeService.award(a.getAuthorId(), BadgeCode.TEACHER, "ANSWER", answerId);
    }
    if (value == -1) {
      badgeService.award(userId, BadgeCode.CRITIC, "ANSWER", answerId);
    }
    if (reputation.reputationOf(a.getAuthorId()) >= RepPoints.LVL_UPVOTE_QUESTION) {
      badgeService.award(a.getAuthorId(), BadgeCode.PHILANTHROPIST, "ANSWER", answerId);
    }
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

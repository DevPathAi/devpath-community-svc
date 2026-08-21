package ai.devpath.community.post;

import ai.devpath.community.post.dto.RevisionView;
import ai.devpath.community.reputation.ReputationService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 콘텐츠 내리기.
 *
 * <p>작성자 삭제({@code DELETED})와 달리 {@code HIDDEN} 은 규정 위반 판단이므로 그 콘텐츠로
 * 얻은 평판을 회수한다. 그리고 ★작성자에게 걸던 "채택된 답변은 못 지운다"(409) 제한을 받지
 * 않는다★ — 규정 위반 답변이 하필 채택된 상태일 수 있고, 그때 내리지 못하면 모더레이션이
 * 무력해진다. 대신 질문의 채택 연결을 풀어 준다.
 */
@Service
public class ContentAdminService {

  private final CommunityPostRepository posts;
  private final CommunityAnswerRepository answers;
  private final CommunityCommentRepository comments;
  private final CommunityQuestionRepository questions;
  private final CommunityPostTagRepository postTags;
  private final ReputationService reputation;
  private final PostIndexEventPublisher postIndexEvents;
  private final ContentRevisionRepository revisions;

  public ContentAdminService(CommunityPostRepository posts, CommunityAnswerRepository answers,
      CommunityCommentRepository comments, CommunityQuestionRepository questions,
      CommunityPostTagRepository postTags, ReputationService reputation,
      PostIndexEventPublisher postIndexEvents, ContentRevisionRepository revisions) {
    this.posts = posts; this.answers = answers; this.comments = comments;
    this.questions = questions; this.postTags = postTags; this.reputation = reputation;
    this.postIndexEvents = postIndexEvents; this.revisions = revisions;
  }

  /**
   * ★작성자 삭제는 모더레이션을 선점하지 못한다★
   *
   * <p>{@code DELETED} 도 내릴 수 있어야 한다. {@code PUBLISHED} 만 받으면 어뷰저가 upvote 를
   * 모은 뒤 스스로 지우는 것만으로 평판을 굳힐 수 있다 — {@code DELETED} 는 설계상 평판을
   * 유지하고, 그 뒤로는 관리자 요청이 영영 404 이기 때문이다.
   *
   * <p>거부하는 것은 <b>이미 내려간 것</b> 뿐이다. 두 번 회수하면 평판이 음수로 흘러내린다
   * ({@code netBySource} 가 멱등이라 총점은 지켜지지만 보정 이벤트가 계속 늘어난다).
   */
  private static boolean takedownable(String status) {
    return !ContentStatus.HIDDEN.equals(status);
  }

  @Transactional
  public void hidePost(long postId) {
    CommunityPost p = posts.findByIdForUpdate(postId)
        .filter(found -> takedownable(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("post " + postId));
    p.setStatus(ContentStatus.HIDDEN);
    posts.save(p);
    reputation.revokeAllForSource("POST", postId, tagIdsOfPost(postId));
    postIndexEvents.publish(postId, true);
  }

  @Transactional
  public void hideAnswer(long answerId) {
    CommunityAnswer a = answers.findByIdForUpdate(answerId)
        .filter(found -> takedownable(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("answer " + answerId));
    a.setStatus(ContentStatus.HIDDEN);
    if (a.isAccepted()) {
      a.setAccepted(false);
    }
    answers.save(a);

    long questionPostId = a.getQuestionId();
    reputation.revokeAllForSource("ANSWER", answerId, tagIdsOfPost(questionPostId));

    CommunityQuestion q = questions.findById(questionPostId).orElse(null);
    if (q != null && Long.valueOf(answerId).equals(q.getAcceptedAnswerId())) {
      q.setAcceptedAnswerId(null);
      q.setSolved(false);
      questions.save(q);
      // 검색 문서에 isSolved 가 실려 있다. 갱신하지 않으면 "해결됨" 인데 답이 없는 상태가 된다.
      // ★단 부모 글이 이미 내려갔으면 upsert 는 삭제된 질문을 색인에 되살린다★ — 그때의
      // 올바른 색인 상태는 "없음" 이므로 삭제 이벤트를 낸다(멱등이라 다시 보내도 안전하다).
      boolean parentGone = posts.findById(questionPostId)
          .map(found -> !ContentStatus.PUBLISHED.equals(found.getStatus()))
          .orElse(true);
      postIndexEvents.publish(questionPostId, parentGone);
    }
  }

  @Transactional
  public void hideComment(long commentId) {
    CommunityComment c = comments.findById(commentId)
        .filter(found -> takedownable(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("comment " + commentId));
    c.setStatus(ContentStatus.HIDDEN);
    comments.save(c);
    // 댓글에는 투표 엔드포인트가 없어 회수할 평판이 없다. 색인 문서에도 댓글은 없다.
  }

  @Transactional(readOnly = true)
  public List<RevisionView> revisionsOf(String targetType, long targetId) {
    return revisions.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId).stream()
        .map(r -> new RevisionView(r.getTargetType(), r.getTargetId(), r.getTitle(),
            r.getBodyMd(), r.getEditedBy(), r.getCreatedAt()))
        .toList();
  }

  private List<Long> tagIdsOfPost(long postId) {
    return postTags.findByPostId(postId).stream().map(CommunityPostTag::getTagId).toList();
  }
}

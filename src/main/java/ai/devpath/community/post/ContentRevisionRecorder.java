package ai.devpath.community.post;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수정 직전 본문을 이력에 남긴다.
 *
 * <p>★같은 내용으로 저장을 눌러도 이력이 늘지 않는다★ — 직전 리비전과 제목·본문이 모두 같으면
 * 기록하지 않는다. 그러지 않으면 "저장" 을 반복하는 것만으로 이력이 부풀어 신고 처리 때
 * 실제 변경을 찾기 어려워진다.
 *
 * <p>{@code PostIndexEventPublisher} 와 같이 자체 {@code @Transactional} 로 호출자의 트랜잭션에
 * 합류한다(REQUIRED 전파).
 */
@Component
public class ContentRevisionRecorder {

  private final ContentRevisionRepository revisions;

  public ContentRevisionRecorder(ContentRevisionRepository revisions) {
    this.revisions = revisions;
  }

  /** @return 실제로 기록했으면 true, 직전과 같아 건너뛰었으면 false. */
  @Transactional
  public boolean record(String targetType, long targetId, String title, String bodyMd,
      String bodyHtml, long editedBy) {
    List<ContentRevision> prior =
        revisions.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    if (!prior.isEmpty()) {
      ContentRevision last = prior.get(0);
      if (Objects.equals(last.getTitle(), title) && Objects.equals(last.getBodyMd(), bodyMd)) {
        return false;
      }
    }
    ContentRevision r = new ContentRevision();
    r.setTargetType(targetType);
    r.setTargetId(targetId);
    r.setTitle(title);
    r.setBodyMd(bodyMd);
    r.setBodyHtml(bodyHtml);
    r.setEditedBy(editedBy);
    revisions.save(r);
    return true;
  }
}

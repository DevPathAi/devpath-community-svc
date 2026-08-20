package ai.devpath.community.post;

/**
 * 커뮤니티 콘텐츠 상태. DB CHECK 제약과 값이 일치해야 한다
 * (chk_community_posts_status · chk_community_answers_status · chk_community_comments_status).
 *
 * <p>★상태가 삭제 주체를 구분한다★ — {@link #DELETED} 는 작성자가 지운 것이고 평판을 그대로 둔다.
 * {@link #HIDDEN} 은 관리자가 내린 것이고 그 콘텐츠로 얻은 평판을 회수한다. 그래서 "누가
 * 지웠는가" 를 담는 별도 컬럼이 없다.
 *
 * <p>{@code DRAFT} 는 community_posts CHECK 에만 있고 코드에서 쓰이지 않아 여기 두지 않는다.
 */
public final class ContentStatus {
  public static final String PUBLISHED = "PUBLISHED";
  public static final String DELETED = "DELETED";
  public static final String HIDDEN = "HIDDEN";

  private ContentStatus() {}
}

package ai.devpath.community.reputation;

/** 평판 점수·레벨·일일상한 상수(문서 20 §3). */
public final class RepPoints {
  private RepPoints() {}

  public static final int UPVOTE_QUESTION = 5;
  public static final int UPVOTE_ANSWER = 10;
  public static final int ACCEPTED = 15;
  public static final int ACCEPT_BONUS = 2;
  public static final int DOWNVOTE_RECEIVED = -2;
  public static final int DOWNVOTE_CAST = -1;

  /** 하루 upvote 획득 상한(초과분 미지급). */
  public static final int DAILY_UPVOTE_CAP = 40;

  // 레벨 임계(이번 빌드는 15·125만 게이트 강제).
  public static final int LVL_UPVOTE_QUESTION = 15;
  public static final int LVL_DOWNVOTE_ANSWER = 125;
  public static final int LVL_EDIT_SUGGEST = 500;
  public static final int LVL_MODERATION = 1000;
}

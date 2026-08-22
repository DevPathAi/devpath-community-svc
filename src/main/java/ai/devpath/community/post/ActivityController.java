package ai.devpath.community.post;

import ai.devpath.community.post.dto.ActivityView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 마이페이지 활동 요약: 내가 작성한 질문/답변 수. */
@RestController
@RequestMapping("/community/me/activity")
public class ActivityController {

  private final CommunityPostRepository posts;
  private final CommunityAnswerRepository answers;

  public ActivityController(CommunityPostRepository posts, CommunityAnswerRepository answers) {
    this.posts = posts;
    this.answers = answers;
  }

  @GetMapping
  public ActivityView get(@AuthenticationPrincipal Jwt jwt) {
    long uid = Long.parseLong(jwt.getSubject());
    // 지운 글·답변은 내 활동에서도 빠진다. 남아 있으면 "지워지지 않았다" 고 읽힌다.
    return new ActivityView(
        posts.countByAuthorIdAndStatus(uid, ContentStatus.PUBLISHED),
        answers.countByAuthorIdAndAiGeneratedFalseAndStatus(uid, ContentStatus.PUBLISHED));
  }
}

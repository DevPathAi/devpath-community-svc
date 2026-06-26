package ai.devpath.community.post;

import ai.devpath.community.post.dto.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/community")
public class CommunityController {
  private final QuestionService questionService;
  private final AnswerService answerService;

  public CommunityController(QuestionService questionService, AnswerService answerService) {
    this.questionService = questionService;
    this.answerService = answerService;
  }

  @PostMapping("/questions")
  public ResponseEntity<QuestionDetailView> create(
      @AuthenticationPrincipal Jwt jwt, @RequestBody CreateQuestionRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(questionService.create(uid(jwt), req));
  }

  @PostMapping("/questions/{id}/answers")
  public ResponseEntity<AnswerView> answer(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long id,
      @RequestBody CreateAnswerRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(answerService.add(uid(jwt), id, req));
  }

  @PostMapping("/answers/{id}/accept")
  public ResponseEntity<Void> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    answerService.accept(uid(jwt), id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/questions/{id}")
  public ResponseEntity<QuestionDetailView> detail(@PathVariable long id) {
    return ResponseEntity.ok(questionService.detail(id));
  }

  @GetMapping("/posts")
  public ResponseEntity<List<PostSummaryView>> list(
      @RequestParam(required = false) String board,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String sort) {
    return ResponseEntity.ok(questionService.list(board, tag, sort));
  }

  static long uid(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}

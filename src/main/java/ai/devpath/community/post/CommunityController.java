package ai.devpath.community.post;

import ai.devpath.community.post.dto.*;
import ai.devpath.community.seed.EmbeddingClient;
import ai.devpath.community.seed.EmbeddingUnavailableException;
import ai.devpath.community.seed.SimilarQuestionMatcher;
import ai.devpath.community.seed.dto.SimilarQuestionView;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/community")
public class CommunityController {

  private static final Logger log = LoggerFactory.getLogger(CommunityController.class);

  private final QuestionService questionService;
  private final AnswerService answerService;
  private final VoteService voteService;
  private final TagService tagService;
  private final EmbeddingClient embeddingClient;
  private final SimilarQuestionMatcher similarQuestionMatcher;

  public CommunityController(QuestionService questionService, AnswerService answerService,
      VoteService voteService, TagService tagService, EmbeddingClient embeddingClient,
      SimilarQuestionMatcher similarQuestionMatcher) {
    this.questionService = questionService;
    this.answerService = answerService;
    this.voteService = voteService;
    this.tagService = tagService;
    this.embeddingClient = embeddingClient;
    this.similarQuestionMatcher = similarQuestionMatcher;
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

  @GetMapping("/questions/similar")
  public ResponseEntity<List<SimilarQuestionView>> similar(@RequestParam(required = false) String q) {
    if (q == null || q.isBlank()) {
      return ResponseEntity.ok(List.of());
    }
    try {
      List<Double> embedding = embeddingClient.embed(q);
      return ResponseEntity.ok(similarQuestionMatcher.match(embedding, 5));
    } catch (EmbeddingUnavailableException e) {
      log.warn("유사질문 임베딩 실패 — 빈 결과 반환: {}", e.getMessage());
      return ResponseEntity.ok(List.of());
    }
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

  @PostMapping("/posts/{id}/vote")
  public ResponseEntity<Void> votePost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
      @RequestBody VoteRequest req) {
    voteService.votePost(uid(jwt), id, req.value());
    return ResponseEntity.ok().build();
  }

  @PostMapping("/answers/{id}/vote")
  public ResponseEntity<Void> voteAnswer(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
      @RequestBody VoteRequest req) {
    voteService.voteAnswer(uid(jwt), id, req.value());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/tags")
  public ResponseEntity<List<TagView>> tags(@RequestParam(required = false) String q) {
    return ResponseEntity.ok(tagService.autocomplete(q));
  }

  static long uid(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}

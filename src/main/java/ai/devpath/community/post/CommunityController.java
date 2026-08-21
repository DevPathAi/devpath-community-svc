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
  private final PostService postService;
  private final CommentService commentService;

  public CommunityController(QuestionService questionService, AnswerService answerService,
      VoteService voteService, TagService tagService, EmbeddingClient embeddingClient,
      SimilarQuestionMatcher similarQuestionMatcher,
      PostService postService, CommentService commentService) {
    this.questionService = questionService;
    this.answerService = answerService;
    this.voteService = voteService;
    this.tagService = tagService;
    this.embeddingClient = embeddingClient;
    this.similarQuestionMatcher = similarQuestionMatcher;
    this.postService = postService;
    this.commentService = commentService;
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

  @PostMapping("/posts")
  public ResponseEntity<PostDetailView> createPost(
      @AuthenticationPrincipal Jwt jwt, @RequestBody CreatePostRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(uid(jwt), req));
  }

  @GetMapping("/posts/{id}")
  public ResponseEntity<PostDetailView> postDetail(@PathVariable long id) {
    return ResponseEntity.ok(postService.postDetail(id));
  }

  @PutMapping("/posts/{id}")
  public ResponseEntity<PostDetailView> updatePost(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody UpdatePostRequest req) {
    return ResponseEntity.ok(postService.updatePost(uid(jwt), id, req));
  }

  @DeleteMapping("/posts/{id}")
  public ResponseEntity<Void> deletePost(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    postService.deletePost(uid(jwt), id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/posts/{id}/comments")
  public ResponseEntity<CommentView> addComment(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody CreateCommentRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(commentService.addComment(uid(jwt), id, req));
  }

  @GetMapping("/posts/{id}/comments")
  public ResponseEntity<java.util.List<CommentView>> listComments(@PathVariable long id) {
    return ResponseEntity.ok(commentService.listComments(id));
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

  @PutMapping("/answers/{id}")
  public ResponseEntity<AnswerView> updateAnswer(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody UpdateBodyRequest req) {
    return ResponseEntity.ok(answerService.update(uid(jwt), id, req));
  }

  @DeleteMapping("/answers/{id}")
  public ResponseEntity<Void> deleteAnswer(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    answerService.delete(uid(jwt), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/tags")
  public ResponseEntity<List<TagView>> tags(@RequestParam(required = false) String q) {
    return ResponseEntity.ok(tagService.autocomplete(q));
  }

  static long uid(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}

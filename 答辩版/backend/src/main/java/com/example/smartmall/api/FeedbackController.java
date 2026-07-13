package com.example.smartmall.api;

import com.example.smartmall.domain.UserFeedback;
import com.example.smartmall.repo.*;
import com.example.smartmall.audit.AdminOperation;
import com.example.smartmall.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class FeedbackController {
  private final UserFeedbackRepository feedback;
  private final UserRepository users;

  public FeedbackController(UserFeedbackRepository feedback, UserRepository users) {
    this.feedback = feedback;
    this.users = users;
  }

  @PostMapping("/feedback")
  ResponseEntity<FeedbackResponse> create(@AuthenticationPrincipal CurrentUser user,
                                          @Valid @RequestBody FeedbackRequest request) {
    if (request.type() < 1 || request.type() > 3) {
      throw BizException.badRequest("反馈类型不合法");
    }
    UserFeedback item = new UserFeedback();
    item.userId = user.id();
    item.type = request.type();
    item.content = request.content();
    item.status = 0;
    item.createdAt = LocalDateTime.now();
    UserFeedback saved = feedback.save(item);
    return ResponseEntity.status(HttpStatus.CREATED).body(FeedbackResponse.from(saved, user.username()));
  }

  @GetMapping("/admin/feedback")
  @PreAuthorize("hasRole('ADMIN')")
  List<FeedbackResponse> list() {
    return feedback.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
  }

  @PutMapping("/admin/feedback/{id}/reply")
  @PreAuthorize("hasRole('ADMIN')")
  @AdminOperation(action = "feedback.reply", targetType = "feedback")
  FeedbackResponse reply(@PathVariable Long id, @Valid @RequestBody FeedbackReplyRequest request) {
    UserFeedback item = feedback.findById(id).orElseThrow(() -> BizException.notFound("反馈不存在"));
    item.reply = request.reply();
    item.status = 1;
    return toResponse(feedback.save(item));
  }

  private FeedbackResponse toResponse(UserFeedback item) {
    String username = users.findById(item.userId).map(u -> u.username).orElse("用户" + item.userId);
    return FeedbackResponse.from(item, username);
  }
}

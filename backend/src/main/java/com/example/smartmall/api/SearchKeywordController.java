package com.example.smartmall.api;

import com.example.smartmall.audit.AdminOperation;
import com.example.smartmall.domain.SearchKeywordLog;
import com.example.smartmall.repo.SearchKeywordLogRepository;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class SearchKeywordController {
  private final SearchKeywordLogRepository keywords;

  public SearchKeywordController(SearchKeywordLogRepository keywords) {
    this.keywords = keywords;
  }

  @GetMapping("/search/hot-keywords")
  List<HotKeywordResponse> hotKeywords() {
    return keywords.hotKeywords(LocalDateTime.now().minusDays(7), PageRequest.of(0, 8)).stream()
        .map(row -> new HotKeywordResponse(String.valueOf(row[0]), ((Number) row[1]).longValue(), false, (LocalDateTime) row[2]))
        .toList();
  }

  @GetMapping("/admin/search-keywords")
  @PreAuthorize("hasRole('ADMIN')")
  PageResponse<HotKeywordResponse> adminKeywords(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
    List<Object[]> result = keywords.adminStats(PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), 100)));
    List<HotKeywordResponse> list = result.stream()
        .map(row -> new HotKeywordResponse(String.valueOf(row[0]), ((Number) row[1]).longValue(),
            ((Number) row[2]).intValue() == 1, (LocalDateTime) row[3]))
        .toList();
    return new PageResponse<>(keywords.countKeywordGroups(), list);
  }

  @PutMapping("/admin/search-keywords/{keyword}/block")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  @AdminOperation(action = "keyword.block", targetType = "search_keyword")
  HotKeywordResponse block(@PathVariable String keyword, @Valid @RequestBody KeywordBlockRequest request) {
    int blocked = Boolean.FALSE.equals(request.blocked()) ? 0 : 1;
    int updated = keywords.updateBlocked(keyword, blocked);
    if (updated == 0 && blocked == 1) {
      SearchKeywordLog log = new SearchKeywordLog();
      log.keyword = keyword;
      log.isBlocked = 1;
      log.searchedAt = LocalDateTime.now();
      keywords.save(log);
    }
    return new HotKeywordResponse(keyword, 0, blocked == 1, LocalDateTime.now());
  }
}

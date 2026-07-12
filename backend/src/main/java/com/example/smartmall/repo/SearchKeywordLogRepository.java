package com.example.smartmall.repo;

import com.example.smartmall.domain.SearchKeywordLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SearchKeywordLogRepository extends JpaRepository<SearchKeywordLog, Long> {
  @Query("""
      select lower(k.keyword), count(k), max(k.searchedAt)
      from SearchKeywordLog k
      where k.searchedAt >= :since
        and k.isBlocked = 0
        and lower(k.keyword) not in (
          select lower(b.keyword) from SearchKeywordLog b where b.isBlocked = 1
        )
      group by lower(k.keyword)
      order by count(k) desc, max(k.searchedAt) desc
      """)
  List<Object[]> hotKeywords(@Param("since") LocalDateTime since, Pageable pageable);

  @Query("""
      select lower(k.keyword), count(k), max(k.isBlocked), max(k.searchedAt)
      from SearchKeywordLog k
      group by lower(k.keyword)
      order by max(k.searchedAt) desc
      """)
  List<Object[]> adminStats(Pageable pageable);

  @Query("select count(distinct lower(k.keyword)) from SearchKeywordLog k")
  long countKeywordGroups();

  @Modifying
  @Query("update SearchKeywordLog k set k.isBlocked = :blocked where lower(k.keyword) = lower(:keyword)")
  int updateBlocked(@Param("keyword") String keyword, @Param("blocked") Integer blocked);
}

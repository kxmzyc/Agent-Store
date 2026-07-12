package com.example.smartmall.repo;

import com.example.smartmall.domain.BannerSlot;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerSlotRepository extends JpaRepository<BannerSlot, Long> {
  @EntityGraph(attributePaths = "product")
  List<BannerSlot> findByIsActiveOrderBySortOrderAscIdAsc(Integer isActive);

  @EntityGraph(attributePaths = "product")
  List<BannerSlot> findAllByOrderBySortOrderAscIdAsc();
}

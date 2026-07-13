package com.example.smartmall.api;

import com.example.smartmall.audit.AdminOperation;
import com.example.smartmall.domain.ProductTag;
import com.example.smartmall.repo.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api/admin/tags")
@PreAuthorize("hasRole('ADMIN')")
public class TagController {
  private final ProductTagRepository tags;
  private final ProductTagRelationRepository relations;

  public TagController(ProductTagRepository tags, ProductTagRelationRepository relations) {
    this.tags = tags;
    this.relations = relations;
  }

  @GetMapping
  List<TagResponse> list() {
    return tags.findAllByOrderByNameAsc().stream().map(TagResponse::from).toList();
  }

  @PostMapping
  @AdminOperation(action = "tag.create", targetType = "product_tag")
  ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request) {
    String name = request.name().trim();
    if (tags.existsByName(name)) throw BizException.badRequest("标签已存在");
    ProductTag tag = new ProductTag();
    tag.name = name;
    return ResponseEntity.status(HttpStatus.CREATED).body(TagResponse.from(tags.save(tag)));
  }

  @PutMapping("/{id}")
  @AdminOperation(action = "tag.update", targetType = "product_tag")
  TagResponse update(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
    ProductTag tag = tags.findById(id).orElseThrow(() -> BizException.notFound("标签不存在"));
    String name = request.name().trim();
    if (!name.equals(tag.name) && tags.existsByName(name)) throw BizException.badRequest("标签已存在");
    tag.name = name;
    return TagResponse.from(tags.save(tag));
  }

  @DeleteMapping("/{id}")
  @Transactional
  @AdminOperation(action = "tag.delete", targetType = "product_tag")
  Map<String, Object> delete(@PathVariable Long id) {
    ProductTag tag = tags.findById(id).orElseThrow(() -> BizException.notFound("标签不存在"));
    relations.deleteByTagId(tag.id);
    tags.delete(tag);
    return Map.of("ok", true);
  }
}

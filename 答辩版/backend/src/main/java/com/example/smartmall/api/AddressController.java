package com.example.smartmall.api;

import com.example.smartmall.domain.ShippingAddress;
import com.example.smartmall.repo.ShippingAddressRepository;
import com.example.smartmall.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static com.example.smartmall.api.ApiSupport.*;

@RestController
@RequestMapping("/api")
public class AddressController {
  private final ShippingAddressRepository addresses;

  public AddressController(ShippingAddressRepository addresses) {
    this.addresses = addresses;
  }

  @GetMapping("/addresses")
  List<AddressResponse> list(@AuthenticationPrincipal CurrentUser user) {
    return addresses.findByUserIdOrderByIsDefaultDescIdDesc(user.id()).stream()
        .map(AddressResponse::from)
        .toList();
  }

  @PostMapping("/addresses")
  @Transactional
  ResponseEntity<AddressResponse> create(@AuthenticationPrincipal CurrentUser user,
                                         @Valid @RequestBody AddressRequest request) {
    ShippingAddress address = new ShippingAddress();
    apply(address, user.id(), request);
    address.createdAt = LocalDateTime.now();
    if (Boolean.TRUE.equals(request.isDefault()) || addresses.countByUserId(user.id()) == 0) {
      addresses.clearDefault(user.id());
      address.isDefault = 1;
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(AddressResponse.from(addresses.save(address)));
  }

  @PutMapping("/addresses/{id}")
  @Transactional
  AddressResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                         @Valid @RequestBody AddressRequest request) {
    ShippingAddress address = addresses.findByIdAndUserId(id, user.id())
        .orElseThrow(() -> BizException.notFound("地址不存在"));
    apply(address, user.id(), request);
    if (Boolean.TRUE.equals(request.isDefault())) {
      addresses.clearDefault(user.id());
      address.isDefault = 1;
    }
    return AddressResponse.from(addresses.save(address));
  }

  @DeleteMapping("/addresses/{id}")
  Map<String, Object> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    ShippingAddress address = addresses.findByIdAndUserId(id, user.id())
        .orElseThrow(() -> BizException.notFound("地址不存在"));
    addresses.delete(address);
    return Map.of("ok", true);
  }

  @PutMapping("/addresses/{id}/default")
  @Transactional
  AddressResponse setDefault(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    ShippingAddress address = addresses.findByIdAndUserId(id, user.id())
        .orElseThrow(() -> BizException.notFound("地址不存在"));
    addresses.clearDefault(user.id());
    address.isDefault = 1;
    return AddressResponse.from(addresses.save(address));
  }

  private void apply(ShippingAddress address, Long userId, AddressRequest request) {
    address.userId = userId;
    address.receiverName = request.receiverName();
    address.phone = request.phone();
    address.province = request.province();
    address.city = request.city();
    address.district = request.district();
    address.detailAddress = request.detailAddress();
    address.isDefault = Boolean.TRUE.equals(request.isDefault()) ? 1 : 0;
  }
}

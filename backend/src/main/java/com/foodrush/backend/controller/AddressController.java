package com.foodrush.backend.controller;

import com.foodrush.backend.dto.AddressDTO;
import com.foodrush.backend.dto.AddressRequest;
import com.foodrush.backend.security.UserPrincipal;
import com.foodrush.backend.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<List<AddressDTO>> getAddresses(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(addressService.getUserAddresses(principal.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AddressDTO> getAddress(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable Long id) {
        return ResponseEntity.ok(addressService.getAddressById(principal.getId(), id));
    }

    @PostMapping
    public ResponseEntity<AddressDTO> createAddress(@AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressService.createAddress(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressDTO> updateAddress(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(principal.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long id) {
        addressService.deleteAddress(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}

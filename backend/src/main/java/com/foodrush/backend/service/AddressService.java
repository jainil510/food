package com.foodrush.backend.service;

import com.foodrush.backend.dto.AddressDTO;
import com.foodrush.backend.dto.AddressRequest;
import com.foodrush.backend.entity.Address;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.AddressNotFoundException;
import com.foodrush.backend.repository.AddressRepository;
import com.foodrush.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressDTO> getUserAddresses(Long userId) {
        return addressRepository.findByUserId(userId).stream()
                .map(AddressDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AddressDTO getAddressById(Long userId, Long addressId) {
        return AddressDTO.from(requireOwnedAddress(userId, addressId));
    }

    @Transactional
    public AddressDTO createAddress(Long userId, AddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        Address address = Address.builder()
                .user(user)
                .label(request.label())
                .fullAddress(request.fullAddress())
                .city(request.city())
                .pincode(request.pincode())
                .build();
        return AddressDTO.from(addressRepository.save(address));
    }

    @Transactional
    public AddressDTO updateAddress(Long userId, Long addressId, AddressRequest request) {
        Address address = requireOwnedAddress(userId, addressId);
        address.setLabel(request.label());
        address.setFullAddress(request.fullAddress());
        address.setCity(request.city());
        address.setPincode(request.pincode());
        return AddressDTO.from(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        addressRepository.delete(requireOwnedAddress(userId, addressId));
    }

    /**
     * Scoping the lookup to the caller's own user id is what enforces ownership: another
     * user's address id simply is not here, and reports as 404 exactly like a nonexistent
     * one, so existence is never leaked. Mirrors CartService.requireOwnedItem.
     */
    private Address requireOwnedAddress(Long userId, Long addressId) {
        return addressRepository.findByUserIdAndId(userId, addressId)
                .orElseThrow(() -> new AddressNotFoundException("Address not found: " + addressId));
    }
}

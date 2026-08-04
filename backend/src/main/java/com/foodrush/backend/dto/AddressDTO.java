package com.foodrush.backend.dto;

import com.foodrush.backend.entity.Address;

public record AddressDTO(
        Long id,
        String label,
        String fullAddress,
        String city,
        String pincode
) {

    public static AddressDTO from(Address address) {
        return new AddressDTO(
                address.getId(),
                address.getLabel(),
                address.getFullAddress(),
                address.getCity(),
                address.getPincode());
    }
}

package com.foodrush.backend.service;

import com.foodrush.backend.dto.AddressDTO;
import com.foodrush.backend.dto.AddressRequest;
import com.foodrush.backend.entity.Address;
import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import com.foodrush.backend.exception.AddressNotFoundException;
import com.foodrush.backend.repository.AddressRepository;
import com.foodrush.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    private AddressService addressService;

    @BeforeEach
    void setUp() {
        addressService = new AddressService(addressRepository, userRepository);
    }

    static User user(Long id) {
        return User.builder().id(id).name("Asha").email("asha@foodrush.com")
                .password("hash").role(Role.USER).build();
    }

    static Address address(Long id, User owner, String label, String fullAddress, String city, String pincode) {
        return Address.builder().id(id).user(owner).label(label).fullAddress(fullAddress)
                .city(city).pincode(pincode).build();
    }

    @Test
    void getUserAddresses_returnsAllAddressesForUser() {
        User asha = user(USER_ID);
        when(addressRepository.findByUserId(USER_ID)).thenReturn(List.of(
                address(1L, asha, "Home", "221B Baker Street", "Mumbai", "400001"),
                address(2L, asha, "Work", "12 MG Road", "Mumbai", "400002")));

        List<AddressDTO> result = addressService.getUserAddresses(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).label()).isEqualTo("Home");
        assertThat(result.get(1).label()).isEqualTo("Work");
    }

    @Test
    void getUserAddresses_returnsEmptyList_whenUserHasNone() {
        when(addressRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<AddressDTO> result = addressService.getUserAddresses(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getAddressById_returnsAddress_whenOwnedByUser() {
        User asha = user(USER_ID);
        when(addressRepository.findByUserIdAndId(USER_ID, 1L)).thenReturn(
                Optional.of(address(1L, asha, "Home", "221B Baker Street", "Mumbai", "400001")));

        AddressDTO result = addressService.getAddressById(USER_ID, 1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.fullAddress()).isEqualTo("221B Baker Street");
    }

    @Test
    void getAddressById_throwsNotFound_whenAddressBelongsToAnotherUser() {
        // 42 exists in the database but under a different user id, so the scoped lookup
        // returns empty exactly as it would for a nonexistent id - no existence leak.
        when(addressRepository.findByUserIdAndId(USER_ID, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddressById(USER_ID, 42L))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessage("Address not found: 42");
    }

    @Test
    void getAddressById_throwsNotFound_whenAddressDoesNotExist() {
        when(addressRepository.findByUserIdAndId(USER_ID, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddressById(USER_ID, 99L))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessage("Address not found: 99");
    }

    @Test
    void createAddress_savesAndReturnsNewAddress() {
        User asha = user(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(asha));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            a.setId(1L);
            return a;
        });

        AddressDTO result = addressService.createAddress(USER_ID,
                new AddressRequest("Home", "221B Baker Street", "Mumbai", "400001"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.label()).isEqualTo("Home");
        assertThat(result.fullAddress()).isEqualTo("221B Baker Street");
        assertThat(result.city()).isEqualTo("Mumbai");
        assertThat(result.pincode()).isEqualTo("400001");
    }

    @Test
    void createAddress_attachesTheAuthenticatedUser() {
        User asha = user(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(asha));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        addressService.createAddress(USER_ID, new AddressRequest("Home", "221B Baker Street", "Mumbai", "400001"));

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(asha);
    }

    @Test
    void updateAddress_updatesFieldsAndReturnsUpdated() {
        User asha = user(USER_ID);
        Address existing = address(1L, asha, "Home", "221B Baker Street", "Mumbai", "400001");
        when(addressRepository.findByUserIdAndId(USER_ID, 1L)).thenReturn(Optional.of(existing));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressDTO result = addressService.updateAddress(USER_ID, 1L,
                new AddressRequest("Office", "12 MG Road", "Pune", "411001"));

        assertThat(result.label()).isEqualTo("Office");
        assertThat(result.fullAddress()).isEqualTo("12 MG Road");
        assertThat(result.city()).isEqualTo("Pune");
        assertThat(result.pincode()).isEqualTo("411001");
    }

    @Test
    void updateAddress_throwsNotFound_whenAddressBelongsToAnotherUser() {
        when(addressRepository.findByUserIdAndId(USER_ID, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(USER_ID, 42L,
                new AddressRequest("Office", "12 MG Road", "Pune", "411001")))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessage("Address not found: 42");

        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void deleteAddress_deletesOwnedAddress() {
        User asha = user(USER_ID);
        Address existing = address(1L, asha, "Home", "221B Baker Street", "Mumbai", "400001");
        when(addressRepository.findByUserIdAndId(USER_ID, 1L)).thenReturn(Optional.of(existing));

        addressService.deleteAddress(USER_ID, 1L);

        verify(addressRepository).delete(existing);
    }

    @Test
    void deleteAddress_throwsNotFound_whenAddressBelongsToAnotherUser() {
        when(addressRepository.findByUserIdAndId(USER_ID, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, 42L))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessage("Address not found: 42");

        verify(addressRepository, never()).delete(any(Address.class));
    }
}

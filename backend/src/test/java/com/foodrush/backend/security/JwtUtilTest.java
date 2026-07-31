package com.foodrush.backend.security;

import com.foodrush.backend.entity.Role;
import com.foodrush.backend.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String TEST_SECRET =
            "test-secret-key-must-be-at-least-256-bits-long-for-hs256-signing";

    private final User user = User.builder()
            .id(42L)
            .name("Ada Lovelace")
            .email("ada@foodrush.com")
            .password("hashed-password")
            .role(Role.ADMIN)
            .build();
    private final UserPrincipal principal = new UserPrincipal(user);

    @Test
    void generateToken_thenValidateToken_succeedsForFreshToken() {
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET, 60_000);

        String token = jwtUtil.generateToken(principal);

        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void generateToken_embedsEmailAsSubject() {
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET, 60_000);

        String token = jwtUtil.generateToken(principal);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("ada@foodrush.com");
    }

    @Test
    void generateToken_embedsUserIdClaim() {
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET, 60_000);

        String token = jwtUtil.generateToken(principal);

        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void generateToken_embedsRoleClaim() {
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET, 60_000);

        String token = jwtUtil.generateToken(principal);

        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void validateToken_returnsFalse_forTamperedToken() {
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET, 60_000);
        String token = jwtUtil.generateToken(principal);
        int midpoint = token.length() / 2;
        char flipped = token.charAt(midpoint) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, midpoint) + flipped + token.substring(midpoint + 1);

        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forExpiredToken() {
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET, -1_000);

        String expiredToken = jwtUtil.generateToken(principal);

        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forGarbageInput() {
        JwtUtil jwtUtil = new JwtUtil(TEST_SECRET, 60_000);

        assertThat(jwtUtil.validateToken("not-a-jwt-at-all")).isFalse();
    }

    @Test
    void constructor_throwsIllegalStateException_whenSecretTooShortForHs256() {
        assertThatThrownBy(() -> new JwtUtil("too-short-secret", 60_000))
                .isInstanceOf(IllegalStateException.class);
    }
}

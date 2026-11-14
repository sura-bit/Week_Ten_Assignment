package org.example.weektenassignment;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long ttlMinutes;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.ttlMinutes = ttlMinutes;
    }

    @Transactional
    public AppUser signup(String username, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("password must be at most 72 UTF-8 bytes");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("username " + username + " is taken");
        }
        return userRepository.save(
                new AppUser(username, passwordEncoder.encode(password), "USER"));
    }

    public TokenResponse login(String username, String password) {
        // Unknown user and wrong password get the same message
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("wrong username or password");
        }
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("wrong username or password"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("wrong username or password");
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("room-api")
                .issuedAt(now)
                .expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES))
                .subject(user.getUsername())
                .claim("role", user.getRole())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();   // without it: 500
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttlMinutes * 60);
    }
}
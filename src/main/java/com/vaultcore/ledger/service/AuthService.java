package com.vaultcore.ledger.service;

import com.vaultcore.ledger.config.JwtUtil;
import com.vaultcore.ledger.domain.User;
import com.vaultcore.ledger.dto.AuthRequest;
import com.vaultcore.ledger.dto.AuthResponse;
import com.vaultcore.ledger.dto.RegisterRequest;
import com.vaultcore.ledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        User user = new User(
                request.getName(),
                request.getPhoneNumber(),
                passwordEncoder.encode(request.getPassword()),
                request.getEmail()
        );
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getPhoneNumber(), user.getRoles());
        return new AuthResponse(user.getId(), token, 86400000L);
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new IllegalArgumentException("Invalid phone number or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid phone number or password");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getPhoneNumber(), user.getRoles());
        return new AuthResponse(user.getId(), token, 86400000L);
    }
}

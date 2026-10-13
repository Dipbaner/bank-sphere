package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.dto.RegisterRequest;
import com.nova.tech.bank.sphere.entity.AuthProvider;
import com.nova.tech.bank.sphere.entity.Role;
import com.nova.tech.bank.sphere.entity.User;
import com.nova.tech.bank.sphere.entity.UserStatus;
import com.nova.tech.bank.sphere.exception.DuplicateResourceException;
import com.nova.tech.bank.sphere.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email))
            throw new DuplicateResourceException("Email is already registered");
        if (userRepository.existsByPhone(req.getPhone()))
            throw new DuplicateResourceException("Phone number is already registered");

        User user = User.builder()
                .name(req.getName().trim())
                .email(email)
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(Role.CUSTOMER)                        // never taken from the request
                .status(UserStatus.PENDING_EMAIL_VERIFICATION)
                .provider(AuthProvider.LOCAL)
                .failedAttempts(0)
                .emailVerified(false)
                .build();

        userRepository.save(user);
    }
}

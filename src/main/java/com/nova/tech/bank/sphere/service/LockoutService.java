package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.entity.User;
import com.nova.tech.bank.sphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
@Service
@RequiredArgsConstructor
public class LockoutService {

    private final UserRepository userRepository;

    @Value("${app.security.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${app.security.lock-minutes}")
    private long lockMinutes;

    public boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    public long minutesRemaining(User user) {
        long secs = Duration.between(LocalDateTime.now(), user.getLockedUntil()).getSeconds();
        return Math.max(1, (secs + 59) / 60);   // round up, never show 0
    }

    /** Counts one failed password. Returns true if this failure triggered the lock. */
    @Transactional
    public boolean recordFailedLogin(Long userId) {
        User u = userRepository.findByIdForUpdate(userId).orElseThrow();   // row lock

        // An old lock has expired: start counting from zero again.
        if (u.getLockedUntil() != null && !u.getLockedUntil().isAfter(LocalDateTime.now())) {
            u.setLockedUntil(null);
            u.setFailedAttempts(0);
        }
        // Already locked by a parallel request: do not extend the lock.
        if (u.getLockedUntil() != null) return false;

        u.setFailedAttempts(u.getFailedAttempts() + 1);

        if (u.getFailedAttempts() >= maxFailedAttempts) {
            u.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            userRepository.save(u);
            return true;
        }
        userRepository.save(u);
        return false;
    }

    /** Called after a correct password while the account is not locked. */
    @Transactional
    public void resetFailures(Long userId) {
        User u = userRepository.findByIdForUpdate(userId).orElseThrow();
        if (u.getFailedAttempts() != 0 || u.getLockedUntil() != null) {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
            userRepository.save(u);
        }
    }

    /** Locks the account right now (used when OTP guessing is detected). */
    @Transactional
    public void lockNow(Long userId) {
        User u = userRepository.findByIdForUpdate(userId).orElseThrow();
        u.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
        userRepository.save(u);
    }
}

package com.reconiq.service;

import com.reconiq.dto.LoginRequest;
import com.reconiq.dto.LoginResponse;
import com.reconiq.entity.User;
import com.reconiq.exception.UnauthorizedException;
import com.reconiq.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmployeeIdIgnoreCase(request.employeeId().trim())
                // Same message for unknown user and wrong password to avoid account enumeration.
                .orElseThrow(() -> new UnauthorizedException("Invalid employee ID or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid employee ID or password");
        }

        return new LoginResponse(
                user.getId(),
                user.getEmployeeId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name());
    }
}

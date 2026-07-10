package com.mockinterview.service;

import com.mockinterview.dto.AuthResponse;
import com.mockinterview.dto.LoginRequest;
import com.mockinterview.dto.RegisterRequest;
import com.mockinterview.dto.UserDTO;
import com.mockinterview.entity.User;
import com.mockinterview.exception.ResourceNotFoundException;
import com.mockinterview.mapper.UserMapper;
import com.mockinterview.repository.UserRepository;
import com.mockinterview.security.JwtService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final UserDetailsService userDetailsService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuthenticationManager authenticationManager, UserMapper userMapper, UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userMapper = userMapper;
        this.userDetailsService = userDetailsService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .profileImage("")
                .build();

        userRepository.save(user);

        var springUser = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles("USER")
                .build();

        String jwtToken = jwtService.generateToken(springUser);
        String refreshToken = jwtService.generateRefreshToken(springUser);

        return AuthResponse.builder()
                .token(jwtToken)
                .refreshToken(refreshToken)
                .user(userMapper.toDTO(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var springUser = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles("USER")
                .build();

        String jwtToken = jwtService.generateToken(springUser);
        String refreshToken = jwtService.generateRefreshToken(springUser);

        return AuthResponse.builder()
                .token(jwtToken)
                .refreshToken(refreshToken)
                .user(userMapper.toDTO(user))
                .build();
    }

    /**
     * Validate a refresh token, rotate it, and issue a fresh access + refresh pair.
     * Stateless by design (no server-side blacklist), consistent with the rest of the app.
     */
    public com.mockinterview.dto.TokenRefreshResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        final String username = jwtService.extractUsernameFromRefresh(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        if (!jwtService.isRefreshTokenValid(refreshToken, userDetails)) {
            throw new IllegalArgumentException("Refresh token is invalid or expired");
        }

        String newAccessToken = jwtService.generateToken(userDetails);
        String newRefreshToken = jwtService.generateRefreshToken(userDetails);
        return com.mockinterview.dto.TokenRefreshResponse.of(newAccessToken, newRefreshToken, jwtService.getJwtExpiration());
    }

    public UserDTO getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toDTO(user);
    }
}

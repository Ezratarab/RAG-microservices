package com.example.auth_service.Controller;

import com.example.auth_service.DTO.UserDTO;
import com.example.auth_service.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class UserController {

    private final UserService userService;

    /** Public sign-up. @Valid makes the DTO's validation annotations actually run. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createUser(@Valid @RequestBody UserDTO userDTO) {
        return Map.of("id", userService.createUser(userDTO));
    }

    @GetMapping
    @PreAuthorize("hasRole('client_admin')")
    public List<UserDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    /** Kept for existing callers; same data as GET /auth (Keycloak is the only user store now). */
    @GetMapping("/allUsersKC")
    @PreAuthorize("hasRole('admin')")
    public List<UserDTO> getAllUsersKeyCloak() {
        return userService.getAllUsers();
    }

    @GetMapping("/hello")
    @PreAuthorize("hasRole('client_user')")
    public String getHello() {
        return "Hello World";
    }

    @GetMapping("/hellov2")
    @PreAuthorize("hasRole('client_admin')")
    public String getHello2() {
        return "Hello Admin";
    }
}

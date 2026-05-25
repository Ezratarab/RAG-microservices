package com.example.auth_service.Controller;

import com.example.auth_service.DTO.UserDTO;
import com.example.auth_service.Service.KeycloakService;
import com.example.auth_service.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class UserController {

    private final UserService userService;
    private final KeycloakService keycloakService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createUser(@RequestBody UserDTO userDTO) {
        userService.createUser(userDTO);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('client_admin')")
    public List<UserDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/allUsersKC")
    @PreAuthorize("hasRole('admin')")
    @ResponseStatus(HttpStatus.OK)
    public List<UserDTO> getAllUsersKeyCloak() {
        return keycloakService.getAllUsersFromKeyCloak();
    }

    @GetMapping("/hello")
    @PreAuthorize("hasRole('client_user')")
    @ResponseStatus(HttpStatus.OK)
    public String getHello() {
        return "Hello World";
    }

    @GetMapping("/hellov2")
    @PreAuthorize("hasRole('client_admin')")
    @ResponseStatus(HttpStatus.OK)
    public String getHello2() {
        return "Hello Admin";
    }
}

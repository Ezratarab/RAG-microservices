package com.example.auth_service.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    private Long customerId;

    @NotBlank(message = "username should not be empty")
    private String username;

    @NotBlank(message = "phone should not be empty")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number")
    private String phone;

    @NotBlank(message = "email should not be empty")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "password should not be empty")
    private String password;
}

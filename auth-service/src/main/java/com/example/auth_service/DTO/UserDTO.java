package com.example.auth_service.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    /** Keycloak user id. Set by the server, ignored on input. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String id;

    @NotBlank(message = "username should not be empty")
    @Size(max = 50)
    private String username;

    @NotBlank(message = "phone should not be empty")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number")
    private String phone;

    @NotBlank(message = "email should not be empty")
    @Email(message = "Invalid email format")
    @Size(max = 254)
    private String email;

    /** Accepted on sign-up, never written back in a response or a log line. */
    @NotBlank(message = "password should not be empty")
    @Size(min = 8, max = 128)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;
}

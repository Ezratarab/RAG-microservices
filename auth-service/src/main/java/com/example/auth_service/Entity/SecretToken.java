package main.java.com.example.auth_service.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Entity
@Table(name = "TOKENS")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SecretToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Timestamp createdAt;

    @Lob
    @Column(name = "TOKEN", columnDefinition = "CLOB")
    private String token;

    @Column(nullable = false)
    private String username;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = new Timestamp(System.currentTimeMillis());
        }
    }
}

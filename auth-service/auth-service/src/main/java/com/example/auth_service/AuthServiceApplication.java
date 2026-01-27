package com.example.auth_service;

import com.example.auth_service.Security.SecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.sql.DataSource;
import java.net.URL;
import java.sql.Connection;
import java.util.Locale;

@SpringBootApplication
public class AuthServiceApplication implements CommandLineRunner {

    private final DataSource dataSource;

    public AuthServiceApplication(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Value("${spring.profiles.active}")
    private String activeProfile;

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            System.out.println("✅ AUTH SERVICE Successfully connected to Oracle!");
            System.out.println("Database: " + conn.getMetaData().getDatabaseProductName());
            System.out.println("Version: " + conn.getMetaData().getDatabaseProductVersion());
        } catch (Exception e) {
            System.err.println("❌ Connection failed!");
            e.printStackTrace();
        }
        System.out.println("\n*****************************************************");
        System.out.println("\t\t\tLOADED WITH " + activeProfile.toUpperCase(Locale.ROOT) + " PROFILE");
        System.out.println("*****************************************************");
    }


}
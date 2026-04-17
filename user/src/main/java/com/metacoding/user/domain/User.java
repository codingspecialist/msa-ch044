package com.metacoding.user.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "user_tb")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String username;
    private String email;
    private String password;
    private String roles;
}

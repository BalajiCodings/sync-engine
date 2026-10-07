package com.balaji.sync_engine.security;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * For FIELD_WORKER accounts: the single device identity this user is
     * authorized to act as. Null for SUPERVISOR/ADMIN, who aren't tied to
     * one device. This is what lets us validate that a push claiming to be
     * from "deviceB" actually came from the user authorized as deviceB.
     */
    @Column(unique = true)
    private String deviceId;

    @Column(nullable = false)
    private boolean enabled = true;
}
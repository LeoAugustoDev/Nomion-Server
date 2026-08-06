package com.nomion.setup.domain;

import com.nomion.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "initial_setup_state")
@Getter
@NoArgsConstructor
public class InitialSetupState {


    public static final short SINGLETON_ID = 1;


    @Id
    private Short id;


    @Column(name = "initialized_at")
    private Instant initializedAt;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initialized_by_user_id")
    private User initializedBy;


    @Version
    @Column(nullable = false)
    private long version;


    public boolean isInitialized() {
        return initializedAt != null;
    }


    public void markInitialized(User user) {

        this.initializedAt = Instant.now();
        this.initializedBy = user;
    }
}

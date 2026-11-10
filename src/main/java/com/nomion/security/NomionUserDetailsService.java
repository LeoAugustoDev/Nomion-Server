package com.nomion.security;

import com.nomion.user.repository.UserRepository;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class NomionUserDetailsService implements UserDetailsService {


    private final UserRepository repository;


    public NomionUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }


    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {

        return repository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(user -> user.getPassword() != null)
                .map(user -> new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        user.getPassword(),
                        AuthorityUtils.NO_AUTHORITIES
                ))
                .orElseThrow(() ->
                        new UsernameNotFoundException("Usuário não encontrado"));
    }


    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

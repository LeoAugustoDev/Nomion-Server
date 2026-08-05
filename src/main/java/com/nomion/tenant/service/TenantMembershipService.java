package com.nomion.tenant.service;

import com.nomion.handler.BusinessException;
import com.nomion.tenant.domain.Tenant;
import com.nomion.tenant.domain.TenantMembership;
import com.nomion.tenant.domain.TenantMembershipStatus;
import com.nomion.tenant.repository.TenantMembershipRepository;
import com.nomion.user.domain.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantMembershipService {


    private final TenantMembershipRepository repository;


    public TenantMembershipService(TenantMembershipRepository repository) {
        this.repository = repository;
    }


    @Transactional
    public TenantMembership create(User user, Tenant tenant) {

        if (repository.existsByUser_IdAndTenant_Id(user.getId(), tenant.getId())) {
            throw new BusinessException("Usuário já vinculado ao tenant");
        }

        try {
            return repository.saveAndFlush(TenantMembership.builder()
                    .user(user)
                    .tenant(tenant)
                    .status(TenantMembershipStatus.ACTIVE)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Usuário já vinculado ao tenant");
        }
    }


    @Transactional(readOnly = true)
    public List<TenantMembership> findActiveByUser(UUID userId) {

        return repository.findAllByUser_IdAndStatus(
                        userId,
                        TenantMembershipStatus.ACTIVE
                )
                .stream()
                .sorted(Comparator.comparing(membership ->
                        membership.getTenant().getName()))
                .toList();
    }


    @Transactional(readOnly = true)
    public Optional<TenantMembership> findActive(UUID userId, UUID tenantId) {

        return repository.findByUser_IdAndTenant_IdAndStatus(
                userId,
                tenantId,
                TenantMembershipStatus.ACTIVE
        );
    }

}

package com.lineless.queue_service.service;

import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.exception.BusinessAccessDeniedException;
import com.lineless.queue_service.exception.BusinessInUseException;
import com.lineless.queue_service.exception.BusinessNotFoundException;
import com.lineless.queue_service.exception.BusinessValidationException;
import com.lineless.queue_service.repository.BusinessRepository;
import com.lineless.queue_service.repository.QueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final QueueRepository queueRepository;

    @Transactional
    public Business createBusiness(
            UUID ownerUserId,
            String name,
            String description,
            String category,
            String address,
            String phone,
            String timezone,
            boolean isPublic
    ) {
        validateOwner(ownerUserId);
        validateName(name);
        validateOptionalText(category, "category", 100);
        validateOptionalText(phone, "phone", 50);

        String resolvedTimezone = resolveTimezone(timezone);

        Business business = BeanUtils.instantiateClass(Business.class);
        business.setOwnerUserId(ownerUserId);
        business.setName(name.trim());
        business.setDescription(normalizeOptional(description));
        business.setCategory(normalizeOptional(category));
        business.setAddress(normalizeOptional(address));
        business.setPhone(normalizeOptional(phone));
        business.setTimezone(resolvedTimezone);
        business.setPublic(isPublic);

        return businessRepository.save(business);
    }

    @Transactional
    public Business updateBusiness(
            UUID businessId,
            UUID actingOwnerUserId,
            String name,
            String description,
            String category,
            String address,
            String phone,
            String timezone,
            Boolean isPublic
    ) {
        validateOwner(actingOwnerUserId);
        validateName(name);
        validateOptionalText(category, "category", 100);
        validateOptionalText(phone, "phone", 50);

        Business business = findOwnedBusiness(businessId, actingOwnerUserId);

        business.setName(name.trim());
        business.setDescription(normalizeOptional(description));
        business.setCategory(normalizeOptional(category));
        business.setAddress(normalizeOptional(address));
        business.setPhone(normalizeOptional(phone));
        business.setTimezone(resolveTimezone(timezone));

        if (isPublic != null) {
            business.setPublic(isPublic);
        }

        return businessRepository.save(business);
    }

    @Transactional(readOnly = true)
    public Business getBusinessById(UUID businessId) {
        return businessRepository.findById(requireId(businessId, "business ID"))
                .orElseThrow(() -> new BusinessNotFoundException(businessId));
    }

    @Transactional(readOnly = true)
    public List<Business> getBusinessesByOwner(UUID ownerUserId) {
        validateOwner(ownerUserId);
        return businessRepository.findByOwnerUserId(ownerUserId);
    }

    @Transactional(readOnly = true)
    public List<Business> getPublicBusinesses() {
        return businessRepository.findByIsPublicTrue();
    }

    @Transactional
    public void deleteBusiness(UUID businessId, UUID actingOwnerUserId) {
        validateOwner(actingOwnerUserId);

        Business business = findOwnedBusiness(businessId, actingOwnerUserId);

        if (queueRepository.existsByBusinessId(business.getId())) {
            throw new BusinessInUseException(business.getId());
        }

        businessRepository.delete(business);
    }

    private Business findOwnedBusiness(UUID businessId, UUID ownerUserId) {
        Business business = businessRepository.findById(requireId(businessId, "business ID"))
                .orElseThrow(() -> new BusinessNotFoundException(businessId));

        if (!business.getOwnerUserId().equals(ownerUserId)) {
            throw new BusinessAccessDeniedException(businessId);
        }

        return business;
    }

    private void validateOwner(UUID ownerUserId) {
        requireId(ownerUserId, "owner user ID");
    }

    private UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new BusinessValidationException(fieldName + " is required");
        }
        return id;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessValidationException("Business name is required");
        }
        if (name.trim().length() > 255) {
            throw new BusinessValidationException("Business name must be at most 255 characters");
        }
    }

    private void validateOptionalText(String value, String fieldName, int maxLength) {
        if (value != null && value.trim().length() > maxLength) {
            throw new BusinessValidationException(
                    fieldName + " must be at most " + maxLength + " characters"
            );
        }
    }

    private String resolveTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "Asia/Colombo";
        }

        String normalized = timezone.trim();
        if (normalized.length() > 50) {
            throw new BusinessValidationException("Timezone must be at most 50 characters");
        }

        try {
            ZoneId.of(normalized);
        } catch (ZoneRulesException exception) {
            throw new BusinessValidationException(
                    "Invalid timezone ID: " + normalized
            );
        }

        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

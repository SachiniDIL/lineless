package com.lineless.queue_service.service;

import com.lineless.queue_service.entity.Business;
import com.lineless.queue_service.exception.BusinessAccessDeniedException;
import com.lineless.queue_service.exception.BusinessInUseException;
import com.lineless.queue_service.exception.BusinessNotFoundException;
import com.lineless.queue_service.exception.BusinessValidationException;
import com.lineless.queue_service.repository.BusinessRepository;
import com.lineless.queue_service.repository.QueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private QueueRepository queueRepository;

    private BusinessService businessService;

    @BeforeEach
    void setUp() {
        businessService = new BusinessService(businessRepository, queueRepository);
    }

    @Test
    void shouldCreateBusiness() {
        UUID ownerId = UUID.randomUUID();

        when(businessRepository.save(any(Business.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Business created = businessService.createBusiness(
                ownerId,
                "  Main Business  ",
                "Description",
                "RETAIL",
                "Address",
                "123456",
                "Asia/Colombo",
                true
        );

        assertThat(created.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(created.getName()).isEqualTo("Main Business");
        assertThat(created.isPublic()).isTrue();
        assertThat(created.getTimezone()).isEqualTo("Asia/Colombo");
    }

    @Test
    void shouldRejectCreationWithoutName() {
        assertThatThrownBy(() ->
                businessService.createBusiness(
                        UUID.randomUUID(),
                        " ",
                        null,
                        null,
                        null,
                        null,
                        null,
                        true
                ))
                .isInstanceOf(BusinessValidationException.class);

        verify(businessRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidTimezone() {
        assertThatThrownBy(() ->
                businessService.createBusiness(
                        UUID.randomUUID(),
                        "Timezone Business",
                        null,
                        null,
                        null,
                        null,
                        "Not/A_Timezone",
                        true
                ))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Invalid timezone ID");

        verify(businessRepository, never()).save(any());
    }

    @Test
    void shouldUpdateOwnedBusiness() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Business business = createBusiness(ownerId, "Old Name");

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(business));
        when(businessRepository.save(any(Business.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Business updated = businessService.updateBusiness(
                businessId,
                ownerId,
                "New Name",
                "New Description",
                "FOOD",
                "New Address",
                "987654",
                "Asia/Colombo",
                false
        );

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDescription()).isEqualTo("New Description");
        assertThat(updated.isPublic()).isFalse();
        assertThat(updated.getOwnerUserId()).isEqualTo(ownerId);
    }

    @Test
    void shouldRejectUpdateByAnotherOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Business business = createBusiness(ownerId, "Business");

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(business));

        assertThatThrownBy(() ->
                businessService.updateBusiness(
                        businessId,
                        otherOwnerId,
                        "Changed",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
                .isInstanceOf(BusinessAccessDeniedException.class);

        verify(businessRepository, never()).save(any());
    }

    @Test
    void shouldFindBusinessById() {
        UUID businessId = UUID.randomUUID();
        Business business = createBusiness(UUID.randomUUID(), "Found");

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(business));

        assertThat(businessService.getBusinessById(businessId)).isSameAs(business);
    }

    @Test
    void shouldRejectUnknownBusinessId() {
        UUID businessId = UUID.randomUUID();
        when(businessRepository.findById(businessId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> businessService.getBusinessById(businessId))
                .isInstanceOf(BusinessNotFoundException.class);
    }

    @Test
    void shouldListBusinessesByOwner() {
        UUID ownerId = UUID.randomUUID();
        List<Business> businesses = List.of(
                createBusiness(ownerId, "One"),
                createBusiness(ownerId, "Two")
        );

        when(businessRepository.findByOwnerUserId(ownerId))
                .thenReturn(businesses);

        assertThat(businessService.getBusinessesByOwner(ownerId))
                .containsExactlyElementsOf(businesses);
    }

    @Test
    void shouldListPublicBusinesses() {
        List<Business> businesses = List.of(
                createBusiness(UUID.randomUUID(), "Public One"),
                createBusiness(UUID.randomUUID(), "Public Two")
        );

        when(businessRepository.findByIsPublicTrue())
                .thenReturn(businesses);

        assertThat(businessService.getPublicBusinesses())
                .containsExactlyElementsOf(businesses);
    }

    @Test
    void shouldRejectDeletionByAnotherOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Business business = createBusiness(ownerId, "Business");

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(business));

        assertThatThrownBy(() ->
                businessService.deleteBusiness(businessId, otherOwnerId))
                .isInstanceOf(BusinessAccessDeniedException.class);

        verify(businessRepository, never()).delete(any());
    }

    @Test
    void shouldRejectDeletionWhenBusinessHasQueues() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Business business = createBusiness(ownerId, "Business");
        business.setId(businessId);

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(business));
        when(queueRepository.existsByBusinessId(businessId))
                .thenReturn(true);

        assertThatThrownBy(() ->
                businessService.deleteBusiness(businessId, ownerId))
                .isInstanceOf(BusinessInUseException.class);

        verify(businessRepository, never()).delete(any());
    }

    @Test
    void shouldSuccessfullyDeleteOwnedBusinessWithoutQueues() {
        UUID ownerId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Business business = createBusiness(ownerId, "Business");
        business.setId(businessId);

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(business));
        when(queueRepository.existsByBusinessId(businessId))
                .thenReturn(false);

        businessService.deleteBusiness(businessId, ownerId);

        verify(businessRepository).delete(business);
    }

    private Business createBusiness(UUID ownerId, String name) {
        Business business = BeanUtils.instantiateClass(Business.class);
        business.setOwnerUserId(ownerId);
        business.setName(name);
        business.setTimezone("Asia/Colombo");
        business.setPublic(true);
        return business;
    }
}

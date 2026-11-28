package com.lineless.queue_service.repository;

import com.lineless.queue_service.config.AbstractRepositoryTest;
import com.lineless.queue_service.entity.Business;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Test
    void shouldFindBusinessesByOwnerAndPublicStatus() {

        UUID ownerId = UUID.randomUUID();

        Business ownedPublic = createBusiness(
                ownerId,
                "Public Business",
                true
        );

        Business ownedPrivate = createBusiness(
                ownerId,
                "Private Business",
                false
        );

        createBusiness(
                UUID.randomUUID(),
                "Other Public Business",
                true
        );

        List<Business> ownedBusinesses =
                businessRepository.findByOwnerUserId(ownerId);

        List<Business> publicBusinesses =
                businessRepository.findByIsPublicTrue();

        assertThat(ownedBusinesses)
                .extracting(Business::getId)
                .containsExactlyInAnyOrder(
                        ownedPublic.getId(),
                        ownedPrivate.getId()
                );

        assertThat(publicBusinesses)
                .extracting(Business::getId)
                .contains(ownedPublic.getId());

        assertThat(publicBusinesses)
                .extracting(Business::getId)
                .doesNotContain(ownedPrivate.getId());
    }

    private Business createBusiness(
            UUID ownerUserId,
            String name,
            boolean isPublic
    ) {
        Business business = BeanUtils.instantiateClass(Business.class);

        business.setOwnerUserId(ownerUserId);
        business.setName(name);
        business.setPublic(isPublic);

        return businessRepository.saveAndFlush(business);
    }
}

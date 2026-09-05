package com.ehr.repository;

import com.ehr.entity.TrustedDevice;
import com.ehr.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    Optional<TrustedDevice> findByUserAndDeviceTokenHash(User user, String deviceTokenHash);

    List<TrustedDevice> findAllByUser(User user);

    @Modifying
    void deleteAllByUser(User user);
}

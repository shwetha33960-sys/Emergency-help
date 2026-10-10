package com.emergencyhelp.emergency_help;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyContactRepository
        extends JpaRepository<EmergencyContact, Long> {
}

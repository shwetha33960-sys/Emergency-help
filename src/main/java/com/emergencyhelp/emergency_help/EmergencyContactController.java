
package com.emergencyhelp.emergency_help;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/contacts")
public class EmergencyContactController {

    private final EmergencyContactRepository repository;

    public EmergencyContactController(EmergencyContactRepository repository) {
        this.repository = repository;
    }

    // Get all emergency contacts
    @GetMapping
    public List<EmergencyContact> getAllContacts() {
        return repository.findAll();
    }

    // Validate contact details
    private void validateContact(EmergencyContact contact) {

        if (contact == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Contact details are required.");
        }

        if (contact.getName() == null
                || contact.getName().isBlank()
                || contact.getRelation() == null
                || contact.getRelation().isBlank()
                || contact.getPhone() == null
                || contact.getPhone().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name, relationship, and phone number are required.");
        }

        String name = contact.getName().trim();
        String relation = contact.getRelation().trim();
        String phone = contact.getPhone().trim();
        String digits = phone.replaceAll("\\D", "");

        if (name.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name cannot exceed 100 characters.");
        }

        if (relation.length() > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Relationship cannot exceed 50 characters.");
        }

        if (!phone.matches("^[+0-9() -]{7,20}$")
                || digits.length() < 7
                || digits.length() > 15) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please enter a valid phone number.");
        }

        contact.setName(name);
        contact.setRelation(relation);
        contact.setPhone(phone);
    }

    // Save a new emergency contact
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyContact saveContact(
            @RequestBody EmergencyContact contact) {

        validateContact(contact);

        // Ensure a new database record is created
        contact.setId(null);

        return repository.save(contact);
    }

    // Delete an emergency contact
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContact(@PathVariable Long id) {

        if (!repository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Emergency contact not found.");
        }

        repository.deleteById(id);
    }

    // Update an existing emergency contact
    @PutMapping("/{id}")
    public EmergencyContact updateContact(
            @PathVariable Long id,
            @RequestBody EmergencyContact updatedContact) {

        EmergencyContact existingContact = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Emergency contact not found."));

        validateContact(updatedContact);

        existingContact.setName(updatedContact.getName());
        existingContact.setRelation(updatedContact.getRelation());
        existingContact.setPhone(updatedContact.getPhone());

        return repository.save(existingContact);
    }
}

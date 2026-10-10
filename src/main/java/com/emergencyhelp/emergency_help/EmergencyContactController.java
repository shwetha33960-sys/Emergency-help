
package com.emergencyhelp.emergency_help;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PutMapping;


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

    // Save a new emergency contact
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyContact saveContact(
            @RequestBody EmergencyContact contact) {

        if (contact.getName() == null || contact.getName().isBlank()
                || contact.getRelation() == null || contact.getRelation().isBlank()
                || contact.getPhone() == null || contact.getPhone().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name, relationship, and phone number are required.");
        }

        if (!contact.getPhone().matches("^[+0-9() -]{7,20}$")
                || contact.getPhone().replaceAll("\\D", "").length() < 7
                || contact.getPhone().replaceAll("\\D", "").length() > 15) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please enter a valid phone number.");
        }

        contact.setId(null);
        contact.setName(contact.getName().trim());
        contact.setRelation(contact.getRelation().trim());
        contact.setPhone(contact.getPhone().trim());

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

        if (updatedContact.getName() == null
                || updatedContact.getName().isBlank()
                || updatedContact.getRelation() == null
                || updatedContact.getRelation().isBlank()
                || updatedContact.getPhone() == null
                || updatedContact.getPhone().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name, relationship, and phone number are required.");
        }

        String phone = updatedContact.getPhone().trim();

        if (!phone.matches("^[+0-9() -]{7,20}$")
                || phone.replaceAll("\\D", "").length() < 7
                || phone.replaceAll("\\D", "").length() > 15) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please enter a valid phone number.");
        }

        existingContact.setName(updatedContact.getName().trim());
        existingContact.setRelation(updatedContact.getRelation().trim());
        existingContact.setPhone(phone);

        return repository.save(existingContact);
    }

}

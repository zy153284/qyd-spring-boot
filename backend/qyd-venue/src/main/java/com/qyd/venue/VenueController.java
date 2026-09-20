package com.qyd.venue;

import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {
    private final VenueService service;
    public VenueController(VenueService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR','VENUE_ADMIN')")
    VenueDto create(@Valid @RequestBody VenueRequest request) { return service.create(request); }
    @GetMapping List<VenueDto> list(@RequestParam(required = false) VenueStatus status) { return service.list(status); }
    @GetMapping("/{id}") VenueDto get(@PathVariable String id) { return service.get(id); }
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATOR','VENUE_ADMIN')")
    VenueDto update(@PathVariable String id, @Valid @RequestBody VenueRequest request) {
        return service.update(id, request);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    void delete(@PathVariable String id) { service.delete(id); }

    public record VenueRequest(@NotBlank String name, String address, VenueStatus status) {}
    public record VenueDto(String id, String name, String address, VenueStatus status) {
        static VenueDto from(Venue v) { return new VenueDto(v.getId(), v.name, v.address, v.status); }
    }
}

@Entity
@Table(name = "venue")
class Venue extends BaseEntity {
    @Column(nullable = false, length = 200) String name;
    @Column(length = 500) String address;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) VenueStatus status;
    protected Venue() {}
    Venue(VenueController.VenueRequest r) {
        name = r.name(); address = r.address();
        status = r.status() == null ? VenueStatus.ACTIVE : r.status();
    }
}
enum VenueStatus { ACTIVE, INACTIVE }
interface VenueRepository extends JpaRepository<Venue, String> {
    List<Venue> findAllByStatus(VenueStatus status);
}

@Service
@Transactional
class VenueService {
    private final VenueRepository repository;
    VenueService(VenueRepository repository) { this.repository = repository; }
    VenueController.VenueDto create(VenueController.VenueRequest r) {
        return VenueController.VenueDto.from(repository.save(new Venue(r)));
    }
    @Transactional(readOnly = true)
    List<VenueController.VenueDto> list(VenueStatus status) {
        return (status == null ? repository.findAll() : repository.findAllByStatus(status))
                .stream().map(VenueController.VenueDto::from).toList();
    }
    @Transactional(readOnly = true)
    VenueController.VenueDto get(String id) { return VenueController.VenueDto.from(require(id)); }
    VenueController.VenueDto update(String id, VenueController.VenueRequest r) {
        Venue v = require(id); v.name = r.name(); v.address = r.address();
        if (r.status() != null) v.status = r.status();
        return VenueController.VenueDto.from(v);
    }
    void delete(String id) { repository.delete(require(id)); }
    private Venue require(String id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "venue not found"));
    }
}

package com.qyd.auth;

import com.qyd.shared.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final UserAccountRepository users;private final PasswordEncoder encoder;
    public AccountController(UserAccountRepository users,PasswordEncoder encoder){this.users=users;this.encoder=encoder;}

    @GetMapping("/auth/me")
    AccountDto me(Authentication auth){return AccountDto.of(require(auth.getName()));}

    @GetMapping("/admin/accounts")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    List<AccountDto> accounts(){return users.findAll().stream().map(AccountDto::of).toList();}

    @PostMapping("/admin/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Transactional
    AccountDto create(@Valid @RequestBody CreateAccount request){
        if(users.findByUsername(request.username()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"username already exists");
        return AccountDto.of(users.save(new UserAccount(request.username(),encoder.encode(request.password()),request.role())));
    }

    @PutMapping("/admin/accounts/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Transactional
    AccountDto update(@PathVariable String id,@Valid @RequestBody UpdateAccount request,Authentication auth){
        UserAccount user=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"account not found"));
        if(user.getId().equals(auth.getName())&&!request.enabled())throw new ResponseStatusException(HttpStatus.CONFLICT,"cannot disable current account");
        user.update(request.role(),request.enabled());return AccountDto.of(user);
    }
    private UserAccount require(String principal){return users.findById(principal).or(()->users.findByUsername(principal))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"account not found"));}

    public record CreateAccount(@NotBlank @Size(max=100)String username,@NotBlank @Size(min=8,max=100)String password,@NotNull Role role){}
    public record UpdateAccount(@NotNull Role role,boolean enabled){}
    public record AccountDto(String id,String username,String role,boolean enabled,List<String>permissions){
        static AccountDto of(UserAccount user){return new AccountDto(user.getId(),user.getUsername(),user.getRole().name(),user.isEnabled(),permissions(user.getRole()));}
        private static List<String> permissions(Role role){return switch(role){
            case PLATFORM_ADMIN->List.of("*");
            case OPERATOR->List.of("content:manage","marketing:manage","risk:manage","venue:manage","order:read");
            case FINANCE->List.of("order:read","payment:read","refund:manage","settlement:manage");
            case VENUE_ADMIN->List.of("venue:owned","catalog:owned","inventory:manage","order:owned","verification:execute");
            case VENUE_STAFF->List.of("venue:owned","inventory:manage","order:owned","verification:execute");
            case CUSTOMER->List.of("profile:owned","order:owned");
        };}
    }
}

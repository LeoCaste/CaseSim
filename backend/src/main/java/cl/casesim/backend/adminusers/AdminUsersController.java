package cl.casesim.backend.adminusers;

import cl.casesim.backend.adminusers.dto.AdminUserResponse;
import cl.casesim.backend.adminusers.dto.AdminUserRoleResponse;
import cl.casesim.backend.adminusers.dto.CreateAdminUserRequest;
import cl.casesim.backend.adminusers.dto.UpdateAdminUserRequest;
import cl.casesim.backend.adminusers.dto.UpdateAdminUserStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUsersController {

    private final AdminUsersService adminUsersService;

    public AdminUsersController(AdminUsersService adminUsersService) {
        this.adminUsersService = adminUsersService;
    }

    @GetMapping
    public List<AdminUserResponse> getUsers(@RequestParam(name = "active", defaultValue = "true") String active) {
        return adminUsersService.getUsers(active);
    }

    @GetMapping("/roles")
    public List<AdminUserRoleResponse> getRoles() {
        return adminUsersService.getRoles();
    }

    @GetMapping("/{id}")
    public AdminUserResponse getUserById(@PathVariable UUID id) {
        return adminUsersService.getUserById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse createUser(@Valid @RequestBody CreateAdminUserRequest request) {
        return adminUsersService.createUser(request);
    }

    @PutMapping("/{id}")
    public AdminUserResponse updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateAdminUserRequest request) {
        return adminUsersService.updateUser(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminUserResponse updateUserStatus(@PathVariable UUID id, @Valid @RequestBody UpdateAdminUserStatusRequest request) {
        return adminUsersService.updateUserStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID id) {
        adminUsersService.deleteUser(id);
    }
}

package gg.modl.backend.staff.controller;

import gg.modl.backend.auth.PasswordAuthService;
import gg.modl.backend.infrastructure.authorization.RequiresPanelPermission;
import gg.modl.backend.infrastructure.rest.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequiresPanelPermission("admin.staff.manage.members")
public class UsernameStaffInvitationController {
    private final PanelStaffController staff;
    private final PasswordAuthService accounts;

    public record InviteRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String username,
                                @NotBlank @Size(max = 100) String role) {}

    @PostMapping("/v1/panel/staff/invite")
    public ResponseEntity<gg.modl.proto.modl.v1.InviteResultResponse> invite(
        @Valid @RequestBody InviteRequest body, HttpServletRequest request) {
        String username = accounts.validateAvailableUsername(RequestUtil.getRequestServer(request), null, body.username());
        // The upstream protocol field is an internal account key, no address is collected or required.
        return staff.inviteStaff(gg.modl.proto.modl.v1.InviteStaffRequest.newBuilder()
            .setEmail(username).setRole(body.role()).build(), request);
    }
}

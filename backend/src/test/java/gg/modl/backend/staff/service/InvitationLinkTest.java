package gg.modl.backend.staff.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import gg.modl.backend.database.mongo.repository.*;
import gg.modl.backend.infrastructure.exception.*;
import gg.modl.backend.infrastructure.util.IdGenerator;
import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.service.*;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.staff.data.Invitation;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InvitationLinkTest {
    private final InvitationMongoRepository invitations = mock(InvitationMongoRepository.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final RoleAuthorization authorization = mock(RoleAuthorization.class);
    private final Server server = new Server("Test", "modl_instance", "owner@example.com");
    private final RoleAuthorization.PerformerAuthority performer = new RoleAuthorization.PerformerAuthority("owner@example.com", null, true, true);
    private final InvitationService service = new InvitationService(mock(StaffMongoRepository.class), invitations,
        mock(IdGenerator.class), permissions, authorization, mock(ServerLimitPolicy.class), mock(GeneralSettingsService.class));

    private Invitation invitation() {
        ReflectionTestUtils.setField(service, "baseUrl", "https://moderation.example");
        var invitation = Invitation.builder().id("invite").roleId("role-id").token("private-token")
            .expiresAt(new Date(System.currentTimeMillis() + 60000)).build();
        when(invitations.findById(server, "invite")).thenReturn(Optional.of(invitation));
        when(permissions.getRoleById(server, "role-id")).thenReturn(Optional.of(StaffRole.builder().id("role-id").name("Moderator").build()));
        return invitation;
    }

    @Test void resolvesRoleNameBeforeCheckingShareAuthority() {
        invitation();
        assertEquals("https://moderation.example/accept-invitation?token=private-token", service.invitationLink(server, "invite", performer));
        verify(authorization).assertGrantableRole(server, performer, "Moderator");
    }

    @Test void refusesLinksForExpiredOrMissingInvitations() {
        invitation().setExpiresAt(new Date(0));
        assertThrows(ValidationException.class, () -> service.invitationLink(server, "invite", performer));
        assertThrows(ResourceNotFoundException.class, () -> service.invitationLink(server, "missing", performer));
    }

    @Test void doesNotExposeLinksForRolesTheCallerCannotGrant() {
        invitation();
        when(authorization.assertGrantableRole(server, performer, "Moderator")).thenThrow(new ForbiddenException("Role is above your authority"));
        assertThrows(ForbiddenException.class, () -> service.invitationLink(server, "invite", performer));
    }
}

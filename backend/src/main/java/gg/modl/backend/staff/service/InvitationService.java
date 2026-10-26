package gg.modl.backend.staff.service;

import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.infrastructure.exception.ForbiddenException;
import gg.modl.backend.infrastructure.exception.ValidationException;
import gg.modl.backend.database.mongo.repository.InvitationMongoRepository;
import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.limits.ServerLimitPolicy;
import gg.modl.backend.role.data.StaffRole;
import gg.modl.backend.role.service.PermissionService;
import gg.modl.backend.role.service.RoleAuthorization;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.service.GeneralSettingsService;
import gg.modl.backend.staff.data.Invitation;
import gg.modl.backend.staff.data.Staff;
import gg.modl.backend.staff.dto.request.InviteStaffRequest;
import gg.modl.backend.staff.dto.response.InviteResultResponse;
import gg.modl.backend.staff.dto.response.StaffResponse;
import gg.modl.backend.infrastructure.util.IdGenerator;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {
    private final StaffMongoRepository staffRepository;
    private final InvitationMongoRepository invitationRepository;
    private final IdGenerator idGenerator;
    @org.springframework.beans.factory.annotation.Value("${BASE_URL:http://localhost:8080}")
    private String baseUrl;
    private final PermissionService permissionService;
    private final RoleAuthorization roleAuthorization;
    private final ServerLimitPolicy serverLimitPolicy;
    private final GeneralSettingsService generalSettingsService;

    private static final long INVITATION_EXPIRY_MS = 24 * 60 * 60 * 1000;

    public InviteResultResponse sendInvitations(Server server, InviteStaffRequest request, RoleAuthorization.PerformerAuthority performer) {
        List<String> emailsToInvite = new ArrayList<>();
        if (request.emails() != null && !request.emails().isEmpty()) {
            emailsToInvite.addAll(request.emails());
        } else if (request.email() != null) {
            emailsToInvite.add(request.email());
        }

        if (emailsToInvite.isEmpty()) {
            throw new ValidationException("No emails provided");
        }

        List<String> normalizedEmailsToInvite = emailsToInvite.stream()
            .filter(email -> email != null && !email.isBlank())
            .map(EmailAddressUtil::normalize)
            .distinct()
            .toList();

        if (normalizedEmailsToInvite.isEmpty()) {
            throw new ValidationException("No valid emails provided");
        }
        StaffRole grantedRole = roleAuthorization.assertGrantableRole(server, performer, request.role());

        long staffLimit = staffLimitFor(server);
        long currentStaffCount = staffRepository.countAll(server);
        long pendingInvitationsCount = invitationRepository.countActive(server, new Date());
        long totalCurrentMembers = currentStaffCount + pendingInvitationsCount;

        if (totalCurrentMembers >= staffLimit) {
            throw new ConflictException("Staff account limit reached.");
        }

        int availableSeats = (int) (staffLimit - totalCurrentMembers);
        if (normalizedEmailsToInvite.size() > availableSeats) {
            throw new ConflictException(
                String.format("Cannot invite %d staff members. You only have %d available slot(s) remaining.",
                    normalizedEmailsToInvite.size(), availableSeats)
            );
        }

        List<String> success = new ArrayList<>();
        List<InviteResultResponse.FailedInvite> failed = new ArrayList<>();

        for (String email : normalizedEmailsToInvite) {
            try {
                processInvitation(server, email, grantedRole, failed);
                if (failed.stream().noneMatch(f -> f.email().equals(email))) {
                    success.add(email);
                }
            } catch (Exception e) {
                log.error("Error processing invitation for {}", email, e);
                failed.add(new InviteResultResponse.FailedInvite(email, "Internal server error"));
            }
        }

        String message;
        if (success.isEmpty()) {
            message = "No invitations were sent successfully.";
        } else if (failed.isEmpty()) {
            message = success.size() == 1 ? "Invitation sent successfully." :
                      success.size() + " invitations sent successfully.";
        } else {
            message = success.size() + " invitation(s) sent successfully, " + failed.size() + " failed.";
        }

        return new InviteResultResponse(message, success, failed);
    }

    private long staffLimitFor(Server server) {
        return serverLimitPolicy.resolve(server).getMaxStaffSeats();
    }

    private int availableSeats(Server server) {
        long staffLimit = staffLimitFor(server);
        long current = staffRepository.countAll(server) + invitationRepository.countActive(server, new Date());
        return (int) (staffLimit - current);
    }

    private void processInvitation(Server server, String email, StaffRole role,
                                   List<InviteResultResponse.FailedInvite> failed) {
        String normalizedEmail = EmailAddressUtil.normalize(email);

        if (server.getAdminEmail() != null && normalizedEmail.equalsIgnoreCase(server.getAdminEmail())) {
            failed.add(new InviteResultResponse.FailedInvite(normalizedEmail, "Cannot send invitation to the admin email address."));
            return;
        }

        if (staffRepository.existsByEmailExact(server, normalizedEmail)) {
            failed.add(new InviteResultResponse.FailedInvite(normalizedEmail, "Email is already associated with an existing user."));
            return;
        }

        if (invitationRepository.existsByEmailActive(server, normalizedEmail, new Date())) {
            failed.add(new InviteResultResponse.FailedInvite(normalizedEmail, "An invitation for this email is already pending."));
            return;
        }

        if (availableSeats(server) <= 0) {
            failed.add(new InviteResultResponse.FailedInvite(normalizedEmail,
                "Staff member limit reached. Please remove an existing staff member."));
            return;
        }

        String token = idGenerator.generateToken();
        Date expiresAt = new Date(System.currentTimeMillis() + INVITATION_EXPIRY_MS);

        Invitation invitation = Invitation.builder()
            .email(normalizedEmail)
            .roleId(role.getId())
            .token(token)
            .expiresAt(expiresAt)
            .createdAt(new Date())
            .updatedAt(new Date())
            .build();

        invitationRepository.saveEntity(server, invitation);

    }

    public String invitationLink(Server server, String invitationId, RoleAuthorization.PerformerAuthority performer) {
        Invitation invitation = invitationRepository.findById(server, invitationId).orElseThrow(() ->
            new gg.modl.backend.infrastructure.exception.ResourceNotFoundException("Invitation not found"));
        roleAuthorization.assertGrantableRole(server, performer, resolveInvitationRole(server, invitation.getRoleId()).getName());
        if (invitation.getExpiresAt() == null || invitation.getExpiresAt().before(new Date()))
            throw new ValidationException("Invitation has expired. Renew it before sharing.");
        return baseUrl + "/accept-invitation?token=" + invitation.getToken();
    }

    public boolean resendInvitation(Server server, String invitationId) {
        Invitation invitation = invitationRepository.findById(server, invitationId).orElse(null);

        if (invitation == null) {
            return false;
        }

        String previousToken = invitation.getToken();
        Date previousExpiry = invitation.getExpiresAt();

        String newToken = idGenerator.generateToken();
        Date newExpiry = new Date(System.currentTimeMillis() + INVITATION_EXPIRY_MS);

        invitationRepository.refreshToken(server, invitationId, newToken, newExpiry, new Date());

        return true;
    }

    public StaffResponse acceptInvitation(Server server, String token, String password) {
        gg.modl.backend.auth.PasswordAuthService.validatePassword(password);
        Invitation invitation = invitationRepository.findByToken(server, token).orElse(null);

        if (invitation == null) {
            throw new ValidationException("Invalid or expired invitation token.");
        }

        if (invitation.getExpiresAt() == null || invitation.getExpiresAt().before(new Date())) {
            throw new ValidationException("This invitation has expired. Please request a new invitation.");
        }

        if (staffRepository.existsByEmailExact(server, invitation.getEmail())) {
            throw new ConflictException("A staff member with this email already exists.");
        }
        StaffRole invitationRole = resolveInvitationRole(server, invitation.getRoleId());

        long staffLimit = staffLimitFor(server);
        long occupied = staffRepository.countAll(server) + invitationRepository.countActive(server, new Date()) - 1;
        if (occupied >= staffLimit) {
            throw new ConflictException("Staff member limit reached for this server. Please contact an administrator.");
        }

        String username = generateUsernameFromEmail(invitation.getEmail());
        String uniqueUsername = ensureUniqueUsername(server, username);

        Date now = new Date();
        Staff newStaff = Staff.builder()
            .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(password))
            .email(invitation.getEmail())
            .username(uniqueUsername)
            .roleId(invitation.getRoleId())
            .language(generalSettingsService.getGeneralSettings(server).getDefaultLanguage())
            .createdAt(now)
            .updatedAt(now)
            .build();

        staffRepository.saveEntity(server, newStaff);

        invitationRepository.deleteById(server, invitation.getId());

        return new StaffResponse(
            newStaff.getId(),
            newStaff.getEmail(),
            newStaff.getUsername(),
            invitationRole.getName(),
            "active",
            newStaff.getAssignedMinecraftUuid(),
            newStaff.getAssignedMinecraftUsername(),
            newStaff.getCreatedAt()
        );
    }

    private String generateUsernameFromEmail(String email) {
        String localPart = email.split("@")[0];
        return localPart.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String ensureUniqueUsername(Server server, String baseUsername) {
        String username = baseUsername;
        int counter = 1;

        while (staffRepository.existsByUsername(server, username)) {
            username = baseUsername + counter;
            counter++;
        }

        return username;
    }

    private StaffRole resolveInvitationRole(Server server, String roleId) {
        StaffRole role = permissionService.getRoleById(server, roleId)
            .orElseThrow(() -> new ValidationException(
                "This invitation references a role that no longer exists. Please request a new invitation."));
        if (RoleAuthorization.isSuperAdminRole(role)) {
            throw new ForbiddenException("This invitation role must be reissued by an administrator");
        }
        return role;
    }
}

package gg.modl.backend.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "instance_owner")
@Getter
@RequiredArgsConstructor
public final class OwnerCredentials {
    @Id private final String id;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final String serverName;
}

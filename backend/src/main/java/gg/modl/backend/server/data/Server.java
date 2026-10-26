package gg.modl.backend.server.data;

import gg.modl.backend.database.CollectionName;
import gg.modl.backend.database.mongo.codegen.GenerateMongoFields;

import java.util.Date;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = CollectionName.MODL_SERVERS)
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@GenerateMongoFields
public class Server {

    @NotNull
    @Field(name = "serverName", targetType = FieldType.STRING)
    private final String serverName;

    @Nullable
    @Field(name = "databaseName", targetType = FieldType.STRING)
    private final String databaseName;

    @Id
    @Field(targetType = FieldType.OBJECT_ID)
    private String id;

    @NotNull
    @Field(name = "adminEmail", targetType = FieldType.STRING)
    private String adminEmail;

    @Nullable
    @Field(name = "provisioningStatus", targetType = FieldType.STRING)
    private ProvisioningStatus provisioningStatus;

    @Nullable
    @Field(name = "provisioningNotes", targetType = FieldType.STRING)
    private String provisioningNotes;

    @Nullable
    @Field(name = "storageUsedBytes", targetType = FieldType.INT64)
    private Long storageUsedBytes;

    @Nullable
    @ToString.Exclude
    @Field(name = "apiKey", targetType = FieldType.STRING)
    private String apiKey;

    @Nullable
    @Field(name = "onlinePlayerCount", targetType = FieldType.INT64)
    private Long onlinePlayerCount;

    @Nullable
    @Field(name = "userCount", targetType = FieldType.INT64)
    private Long userCount;

    @Nullable
    @Field(name = "ticketCount", targetType = FieldType.INT64)
    private Long ticketCount;

    @Nullable
    @Field(name = "lastStatsUpdatedAt", targetType = FieldType.DATE_TIME)
    private Date lastStatsUpdatedAt;

    @Nullable
    @Field(name = "lastActivityAt", targetType = FieldType.DATE_TIME)
    private Date lastActivityAt;

    @Nullable
    @Field(name = "createdAt", targetType = FieldType.DATE_TIME)
    private Date createdAt;

    @Nullable
    @Field(name = "updatedAt", targetType = FieldType.DATE_TIME)
    private Date updatedAt;

    @Nullable
    @Field(name = "staffPermissionsUpdatedAt", targetType = FieldType.DATE_TIME)
    private Date staffPermissionsUpdatedAt;

    @Nullable
    @Field(name = "punishmentTypesUpdatedAt", targetType = FieldType.DATE_TIME)
    private Date punishmentTypesUpdatedAt;

    private static final int MAX_PROVISIONING_NOTES_LENGTH = 500;

    @Nullable
    public static String boundProvisioningNotes(@Nullable String notes) {
        return notes != null && notes.length() > MAX_PROVISIONING_NOTES_LENGTH
               ? notes.substring(0, MAX_PROVISIONING_NOTES_LENGTH)
               : notes;
    }
}

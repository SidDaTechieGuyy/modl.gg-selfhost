package gg.modl.backend.ticket.controller;

import static gg.modl.backend.infrastructure.proto.ProtoMapperSupport.stringValue;
import gg.modl.proto.modl.v1.TicketVerificationRequiredResponse;

/** Keeps the upstream access-required wire response for ticket capability checks. */
public final class PublicVerificationProtoMapper {
    private PublicVerificationProtoMapper() {}

    public static TicketVerificationRequiredResponse toVerificationRequiredResponse(String recordId, String ignoredEmailHint) {
        return TicketVerificationRequiredResponse.newBuilder()
            .setRequiresVerification(true)
            .setTicketId(stringValue(recordId))
            .build();
    }
}

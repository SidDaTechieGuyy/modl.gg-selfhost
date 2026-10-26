package gg.modl.backend.ticket.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.modl.backend.infrastructure.exception.GlobalExceptionHandler;
import gg.modl.backend.infrastructure.proto.ProtoBinaryHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtoJsonHttpMessageConverter;
import gg.modl.backend.infrastructure.proto.ProtobufErrorResponseWriter;
import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.backend.infrastructure.rest.RequestAttribute;
import gg.modl.backend.realtime.publish.RealtimeEventPublisher;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.ticket.data.Ticket;
import gg.modl.backend.ticket.data.Ticket.ChatMessage;
import gg.modl.backend.ticket.data.TicketReply;
import gg.modl.backend.ticket.dto.response.TicketResponse;
import gg.modl.backend.ticket.service.PublicAccessProperties;
import gg.modl.backend.ticket.service.PublicRecordAccessService;
import gg.modl.backend.ticket.service.TicketAccessTokenService;
import gg.modl.backend.ticket.service.TicketReplyService;
import gg.modl.backend.ticket.service.TicketService;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicTicketControllerTest {
    private TicketService ticketService;
    private TicketAccessTokenService verificationService;
    private MockMvc mockMvc;
    private Server server;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketService.class);
        TicketReplyService ticketReplyService = mock(TicketReplyService.class);
        verificationService = mock(TicketAccessTokenService.class);
        RealtimeEventPublisher realtimeEventPublisher = mock(RealtimeEventPublisher.class);
        PublicRecordAccessService recordAccessService =
            new PublicRecordAccessService(verificationService, new PublicAccessProperties());
        server = new Server("Demo", "server_demo", "admin@example.com");

        mockMvc = MockMvcBuilders
            .standaloneSetup(new PublicTicketController(ticketService, verificationService, ticketReplyService, recordAccessService, realtimeEventPublisher))
            .setControllerAdvice(new GlobalExceptionHandler(new ProtobufErrorResponseWriter()))
            .setMessageConverters(new ProtoJsonHttpMessageConverter(), new ProtoBinaryHttpMessageConverter(), new JacksonJsonHttpMessageConverter())
            .defaultRequest(get("/").requestAttr(RequestAttribute.SERVER, server))
            .build();
    }

    @Test
    void getTicketStatusRequiresTokenForPrivateTicket() throws Exception {
        Ticket ticket = Ticket.builder()
            .id("TICKET-1")
            .build();
        when(ticketService.getTicketRaw(server, "TICKET-1")).thenReturn(Optional.of(ticket));

        mockMvc.perform(get(RESTMappingV1.PUBLIC_TICKETS + "/TICKET-1/status"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(verificationService);
    }

    @Test
    void getTicketStatusAllowsValidTokenForPrivateTicket() throws Exception {
        Ticket ticket = Ticket.builder()
            .id("TICKET-1")
            .build();
        Date created = new Date(1_700_000_000_000L);
        when(ticketService.getTicketRaw(server, "TICKET-1")).thenReturn(Optional.of(ticket));
        when(verificationService.validateToken(server, "TICKET-1", "valid-token")).thenReturn(true);
        when(ticketService.toResponse(server, ticket)).thenReturn(new TicketResponse("TICKET-1", "support", "support", "Need help", "open", null, "PlayerOne", null, null, null, null, created, false, List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(), null, false, null, List.of()));

        mockMvc.perform(get(RESTMappingV1.PUBLIC_TICKETS + "/TICKET-1/status")
                .param("token", "valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("TICKET-1"))
            .andExpect(jsonPath("$.subject").value("Need help"));
    }

    @Test
    void getTicketReturnsPublicConversationAndSubmittedDataAfterVerification() throws Exception {
        Ticket ticket = Ticket.builder()
            .id("TICKET-1")
            .data(Map.of("creatorEmail", "player@example.com"))
            .build();
        Date created = new Date(1_700_000_000_000L);
        when(ticketService.getTicketRaw(server, "TICKET-1")).thenReturn(Optional.of(ticket));
        when(verificationService.validateToken(server, "TICKET-1", "valid-token")).thenReturn(true);
        when(ticketService.toResponse(server, ticket)).thenReturn(new TicketResponse("TICKET-1", "support", "Support", "Need help", "open", null, "PlayerOne", "uuid-1", "PlayerOne", "Reported", "uuid-2", created, false, List.of(TicketReply.builder().id("reply-1").content("hello").staff(false).creatorIdentifier("browser-secret").build()), List.of(), List.of("tag"), Map.of(
                "description", "Submitted form",
                "email", "player@example.com",
                "contact_email", "legacy@example.com",
                "creatorEmail", "player@example.com",
                "creatorIdentifier", "browser-secret",
                "contactEmail", "appeal@example.com",
                "playerUuid", "uuid-appeal"
            ), Map.of(
                "creatorEmail", "player@example.com",
                "creatorIdentifier", "browser-secret",
                "contactEmail", "appeal@example.com",
                "email", "player@example.com",
                "contact_email", "legacy@example.com",
                "playerUuid", "uuid-appeal"
            ), List.of(ChatMessage.builder().content("message").timestamp(created).build()), null, false, null, List.of()));

        when(ticketService.getPublicFormFieldIds(server, ticket)).thenReturn(Set.of("description"));

        mockMvc.perform(get(RESTMappingV1.PUBLIC_TICKETS + "/TICKET-1")
                .param("token", "valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replies[0].content").value("hello"))
            .andExpect(jsonPath("$.messages[0].content").value("hello"))
            .andExpect(jsonPath("$.formData.description").value("Submitted form"))
            .andExpect(jsonPath("$.formData.creatorEmail").doesNotExist())
            .andExpect(jsonPath("$.formData.creatorIdentifier").doesNotExist())
            .andExpect(jsonPath("$.formData.emailAuthEnabled").doesNotExist())
            .andExpect(jsonPath("$.formData.contactEmail").doesNotExist())
            .andExpect(jsonPath("$.formData.email").doesNotExist())
            .andExpect(jsonPath("$.formData.contact_email").doesNotExist())
            .andExpect(jsonPath("$.formData.playerUuid").doesNotExist())
            .andExpect(jsonPath("$.data.creatorEmail").doesNotExist())
            .andExpect(jsonPath("$.data.creatorIdentifier").doesNotExist())
            .andExpect(jsonPath("$.data.emailAuthEnabled").doesNotExist())
            .andExpect(jsonPath("$.data.contactEmail").doesNotExist())
            .andExpect(jsonPath("$.data.email").doesNotExist())
            .andExpect(jsonPath("$.data.contact_email").doesNotExist())
            .andExpect(jsonPath("$.data.playerUuid").doesNotExist())
            .andExpect(jsonPath("$.replies[0].creatorIdentifier").value(""))
            .andExpect(jsonPath("$.creatorUuid").value(""))
            .andExpect(jsonPath("$.reportedPlayer").value("Reported"))
            .andExpect(jsonPath("$.reportedPlayerUuid").value(""))
            .andExpect(jsonPath("$.chatMessages[0]").doesNotExist());
    }

    @Test
    void getTicketHidesUnattachedRawChatMessages() throws Exception {
        Ticket ticket = Ticket.builder()
            .id("TICKET-1")
            .build();
        Date created = new Date(1_700_000_000_000L);
        when(ticketService.getTicketRaw(server, "TICKET-1")).thenReturn(Optional.of(ticket));
        when(ticketService.toResponse(server, ticket)).thenReturn(new TicketResponse("TICKET-1", "support", "Support", "Need help", "open", null, "PlayerOne", "uuid-1", "PlayerOne", "Reported", "uuid-2", created, false, List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(ChatMessage.builder().content("private chat line").timestamp(created).build()), null, false, null, List.of()));

        when(verificationService.validateToken(server, "TICKET-1", "access-token")).thenReturn(true);
        mockMvc.perform(get(RESTMappingV1.PUBLIC_TICKETS + "/TICKET-1").param("token", "access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creatorUuid").value(""))
            .andExpect(jsonPath("$.reportedPlayer").value("Reported"))
            .andExpect(jsonPath("$.reportedPlayerUuid").value(""))
            .andExpect(jsonPath("$.chatMessages[0]").doesNotExist());
    }

    @Test
    void unfinishedSubmissionRequiresItsCreationCapability() throws Exception {
        when(ticketService.getTicketRaw(server, "TICKET-1")).thenReturn(Optional.of(Ticket.builder().id("TICKET-1").build()));
        mockMvc.perform(post(RESTMappingV1.PUBLIC_TICKETS + "/TICKET-1/submit")
            .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"Need help\"}"))
            .andExpect(status().isForbidden());
    }
}

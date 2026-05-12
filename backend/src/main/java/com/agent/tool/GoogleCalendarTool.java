package com.agent.tool;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GoogleCalendarTool {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String APPLICATION_NAME = "personal-agent";

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;

    public GoogleCalendarTool(
            @Value("${google.client.id}") String clientId,
            @Value("${google.client.secret}") String clientSecret,
            @Value("${google.refresh.token}") String refreshToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    public String execute(String operation, Map<String, Object> input) {
        try {
            return switch (operation) {
                case "get_today_events" -> getEvents(0, 1);
                case "get_week_events"  -> getEvents(0, 7);
                case "get_month_events" -> getEvents(0, 30);
                default -> "Unknown operation: " + operation;
            };
        } catch (Exception e) {
            return "Error querying Google Calendar: " + e.getMessage();
        }
    }

    private Calendar buildCalendarClient() throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setTokenServerUrl(new GenericUrl(TOKEN_URL))
                .setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret))
                .build()
                .setRefreshToken(refreshToken);

        credential.refreshToken();

        return new Calendar.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private String getEvents(int startDayOffset, int days) throws Exception {
        Calendar service = buildCalendarClient();

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long startMs = today.plusDays(startDayOffset).atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = today.plusDays(startDayOffset + days).atStartOfDay(zone).toInstant().toEpochMilli();

        Events events = service.events().list("primary")
                .setTimeMin(new com.google.api.client.util.DateTime(startMs))
                .setTimeMax(new com.google.api.client.util.DateTime(endMs))
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .setMaxResults(250)
                .execute();

        List<Event> items = events.getItems();
        if (items == null || items.isEmpty()) {
            return "No events found in the requested window.";
        }

        return items.stream().map(this::formatEvent).collect(Collectors.joining("\n\n"));
    }

    private String formatEvent(Event event) {
        StringBuilder sb = new StringBuilder();
        String title = event.getSummary() == null ? "(no title)" : event.getSummary();
        sb.append("Title: ").append(title).append("\n");

        EventDateTime start = event.getStart();
        EventDateTime end = event.getEnd();

        if (start.getDate() != null) {
            sb.append("Date: ").append(start.getDate()).append(" (all-day)\n");
            if (end != null && end.getDate() != null && !end.getDate().equals(start.getDate())) {
                sb.append("Until: ").append(end.getDate()).append("\n");
            }
        } else if (start.getDateTime() != null) {
            ZoneId zone = ZoneId.systemDefault();
            LocalDateTime startLdt = LocalDateTime.ofEpochSecond(
                    start.getDateTime().getValue() / 1000, 0, zone.getRules().getOffset(java.time.Instant.now()));
            LocalDateTime endLdt = LocalDateTime.ofEpochSecond(
                    end.getDateTime().getValue() / 1000, 0, zone.getRules().getOffset(java.time.Instant.now()));

            sb.append("Date: ").append(startLdt.format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n");
            sb.append("Time: ")
                    .append(startLdt.format(DateTimeFormatter.ofPattern("HH:mm")))
                    .append(" - ")
                    .append(endLdt.format(DateTimeFormatter.ofPattern("HH:mm")))
                    .append("\n");

            Duration duration = Duration.between(startLdt, endLdt);
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            sb.append("Duration: ");
            if (hours > 0) sb.append(hours).append("h ");
            sb.append(minutes).append("m\n");
        }

        List<EventAttendee> attendees = event.getAttendees();
        if (attendees != null && !attendees.isEmpty()) {
            String list = attendees.stream()
                    .map(a -> {
                        String name = a.getDisplayName() != null ? a.getDisplayName() : a.getEmail();
                        String status = a.getResponseStatus() != null ? " (" + a.getResponseStatus() + ")" : "";
                        return name + status;
                    })
                    .collect(Collectors.joining(", "));
            sb.append("Attendees: ").append(list);
        } else {
            sb.append("Attendees: none");
        }

        return sb.toString();
    }
}

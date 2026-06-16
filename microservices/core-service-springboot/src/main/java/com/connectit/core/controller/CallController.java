package com.connectit.core.controller;

import com.connectit.core.model.CallLog;
import com.connectit.core.model.CallNote;
import com.connectit.core.model.Ticket;
import com.connectit.core.model.User;
import com.connectit.core.repository.UserRepository;
import com.connectit.core.service.CallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;
    private final UserRepository userRepo;

    private String getCurrentUserUid() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private String getCurrentUserName(String uid) {
        return userRepo.findByUid(uid).map(User::getName).orElse(uid);
    }

    @GetMapping("/calls")
    public ResponseEntity<?> getCalls(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String callType,
            @RequestParam(required = false) String priority) {
        return ResponseEntity.ok(callService.searchCalls(search, status, callType, priority));
    }

    @GetMapping("/calls/{id}")
    public ResponseEntity<?> getCallById(@PathVariable Long id) {
        return callService.getCallById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/calls")
    public ResponseEntity<?> createCall(@RequestBody CallLog call) {
        try {
            String uid = getCurrentUserUid();
            String name = getCurrentUserName(uid);
            
            // Auto fill agent details if not provided
            if (call.getAgentUid() == null || call.getAgentUid().isBlank()) {
                call.setAgentUid(uid);
                call.setAgentName(name);
            } else if (call.getAgentName() == null || call.getAgentName().isBlank()) {
                call.setAgentName(getCurrentUserName(call.getAgentUid()));
            }

            CallLog created = callService.createCall(call, uid, name);
            return ResponseEntity.status(201).body(created);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/calls/{id}")
    public ResponseEntity<?> updateCall(@PathVariable Long id, @RequestBody CallLog call) {
        try {
            String uid = getCurrentUserUid();
            String name = getCurrentUserName(uid);
            
            if (call.getAgentUid() != null && !call.getAgentUid().isBlank()) {
                call.setAgentName(getCurrentUserName(call.getAgentUid()));
            }
            
            CallLog updated = callService.updateCall(id, call, uid, name);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/calls/{id}")
    public ResponseEntity<?> deleteCall(@PathVariable Long id) {
        try {
            callService.deleteCall(id);
            return ResponseEntity.ok(Map.of("message", "Call log deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Notes ─────────────────────────────────────────────────────────────────
    @GetMapping("/calls/{id}/notes")
    public ResponseEntity<?> getCallNotes(@PathVariable Long id) {
        return ResponseEntity.ok(callService.getNotes(id));
    }

    @PostMapping("/calls/{id}/notes")
    public ResponseEntity<?> addCallNote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String uid = getCurrentUserUid();
            String name = getCurrentUserName(uid);
            String message = (String) body.get("message");
            if (message == null || message.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
            }
            CallNote note = callService.addNote(id, uid, name, message);
            return ResponseEntity.status(201).body(note);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/calls/{id}/notes/{noteId}")
    public ResponseEntity<?> updateCallNote(@PathVariable Long id, @PathVariable Long noteId, @RequestBody Map<String, Object> body) {
        try {
            String uid = getCurrentUserUid();
            String name = getCurrentUserName(uid);
            String message = (String) body.get("message");
            if (message == null || message.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
            }
            CallNote note = callService.updateNote(noteId, message, uid, name);
            return ResponseEntity.ok(note);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/calls/{id}/notes/{noteId}")
    public ResponseEntity<?> deleteCallNote(@PathVariable Long id, @PathVariable Long noteId) {
        try {
            String uid = getCurrentUserUid();
            String name = getCurrentUserName(uid);
            callService.deleteNote(noteId, uid, name);
            return ResponseEntity.ok(Map.of("message", "Note deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Activities / Audit History ──────────────────────────────────────────
    @GetMapping("/calls/{id}/activities")
    public ResponseEntity<?> getCallActivities(@PathVariable Long id) {
        return ResponseEntity.ok(callService.getActivities(id));
    }

    // ── Convert to Ticket ────────────────────────────────────────────────────
    @PostMapping("/calls/{id}/convert")
    public ResponseEntity<?> convertCallToTicket(@PathVariable Long id) {
        try {
            String uid = getCurrentUserUid();
            String name = getCurrentUserName(uid);
            Ticket ticket = callService.convertToTicket(id, uid, name);
            
            Map<String, Object> response = new HashMap<>();
            response.put("ticketId", String.valueOf(ticket.getId()));
            response.put("ticketNumber", ticket.getTicketNumber());
            response.put("message", "Call converted to ticket successfully.");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Reports / Analytics ──────────────────────────────────────────────────
    @GetMapping("/calls/reports")
    public ResponseEntity<?> getCallReports() {
        try {
            return ResponseEntity.ok(callService.getReports());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

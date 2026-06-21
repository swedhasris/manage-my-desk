package com.connectit.core.service;

import com.connectit.core.model.*;
import com.connectit.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepo;
    private final TicketActivityRepository activityRepo;
    private final NotificationRepository notifRepo;
    private final UserRepository userRepo;
    private final EmailLogRepository emailLogRepo;
    private final EmailService emailService;
    private final CategoryRepository categoryRepo;
    private final AssignmentGroupRepository groupRepo;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final Map<String,Integer> RESPONSE_HOURS = Map.of(
        "1 - Critical",2, "2 - High",4, "3 - Moderate",8, "4 - Low",24
    );
    private static final Map<String,Integer> RESOLUTION_HOURS = Map.of(
        "1 - Critical",4, "2 - High",8, "3 - Moderate",24, "4 - Low",72
    );

    public String resolveGroup(String category) {
        return switch (category != null ? category : "") {
            case "Network"  -> "Network Team";
            case "Hardware" -> "Hardware Support";
            case "Software" -> "App Support";
            case "Database" -> "DBA Team";
            default         -> "Service Desk";
        };
    }

    public String generateTicketNumber() {
        return "INC" + (1_000_000 + new Random().nextInt(9_000_000));
    }
    // ── Create ─────────────────────────────────────────────────────────────────
    @Transactional
    public Ticket createTicket(Map<String,Object> data, String createdBy, String createdByName, boolean hasAdminAccess) {
        if (!hasAdminAccess) {
            data.remove("incidentCategory");
            data.remove("incident_category");
        }

        String priority = (String) data.getOrDefault("priority", "4 - Low");
        String category = (String) data.get("category");
        String group    = (String) data.getOrDefault("assignmentGroup", resolveGroup(category));
        LocalDateTime now = LocalDateTime.now();

        Object rawCompanyId = data.containsKey("companyId") ? data.get("companyId") : data.get("company_id");
        Long companyId = null;
        if (rawCompanyId instanceof Number) {
            companyId = ((Number) rawCompanyId).longValue();
        } else if (rawCompanyId instanceof String && !((String) rawCompanyId).isBlank()) {
            try {
                companyId = Long.parseLong((String) rawCompanyId);
            } catch (NumberFormatException ignored) {}
        }

        Ticket t = Ticket.builder()
            .ticketNumber(generateTicketNumber())
            .caller((String) data.getOrDefault("caller", "System"))
            .callerEmail((String) data.get("callerEmail"))
            .callerUserId((String) data.get("callerUserId"))
            .category((String) data.getOrDefault("category", "Inquiry / Help"))
            .incidentCategory(hasAdminAccess ? (String) data.get("incidentCategory") : null)
            .subcategory((String) data.get("subcategory"))
            .service((String) data.get("service"))
            .title((String) data.get("title"))
            .description((String) data.get("description"))
            .status("New")
            .priority(priority)
            .impact((String) data.getOrDefault("impact", "3 - Low"))
            .urgency((String) data.getOrDefault("urgency", "3 - Low"))
            .channel((String) data.getOrDefault("channel", "Self-service"))
            .assignmentGroup(group)
            .assignedTo((String) data.get("assignedTo"))
            .assignedToName((String) data.get("assignedToName"))
            .createdBy(createdBy)
            .createdByName(createdByName)
            .companyId(companyId)
            .watchList(data.containsKey("watchList") ? (String) data.get("watchList") : (String) data.get("watch_list"))
            .responseDeadline(now.plusHours(RESPONSE_HOURS.getOrDefault(priority, 24)))
            .resolutionDeadline(now.plusHours(RESOLUTION_HOURS.getOrDefault(priority, 72)))
            .slaDelayLogsJson("[]")
            .build();

        resolveNormalizedEntities(t);
        t = ticketRepo.save(t);

        // Timeline entry
        logActivity(t.getId(), "system", "public",
            t.getCreatedBy(), t.getCreatedByName(), "Ticket created", null);

        if (List.of("1 - Critical","2 - High").contains(priority)) {
            logActivity(t.getId(), "system", "internal",
                "System Automation", "System Automation",
                "Manager Notified (High Priority)", "{\"reason\":\"High priority ticket created\"}");
        }

        // In-app notifications
        sendCreateNotifications(t, data);

        // Email notifications
        emailService.notifyTicketCreated(t);

        return t;
    }

    // ── Update ─────────────────────────────────────────────────────────────────
    @Transactional
    public Ticket updateTicket(Long id, Map<String,Object> data, String updatedBy, String updatedByName, boolean hasAdminAccess) {
        Ticket t = ticketRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Ticket not found: " + id));

        String prevStatus         = t.getStatus();
        String prevAssignee       = t.getAssignedTo();
        String prevApprovalStatus = t.getApprovalStatus();
        String prevPriority       = t.getPriority();
        String prevTitle          = t.getTitle();
        String prevDesc           = t.getDescription();
        String prevCategory       = t.getCategory();
        String prevSubcat         = t.getSubcategory();
        String prevGroup          = t.getAssignmentGroup();

        // SLA breach RCA validation
        String newStatus = (String) data.get("status");
        if (List.of("Resolved","Closed").contains(newStatus)) {
            validateRcaIfBreached(t, data);
        }

        // Points
        int points = 0;
        if (List.of("Resolved","Closed").contains(newStatus) && t.getResolvedAt() == null) {
            points = calcPoints(t);
        }

        // Apply fields
        apply(t, data, hasAdminAccess);
        t.setPoints((t.getPoints() == null ? 0 : t.getPoints()) + points);

        if (List.of("Resolved","Closed").contains(newStatus) && t.getResolvedAt() == null) {
            t.setResolvedAt(LocalDateTime.now());
            // Auto-complete SLA timers so the scheduler stops monitoring this ticket
            if (!"Breached".equals(t.getResponseSlaStatus())) {
                t.setResponseSlaStatus("Completed");
            }
            if (!"Breached".equals(t.getResolutionSlaStatus())) {
                t.setResolutionSlaStatus("Completed");
            }
        }

        resolveNormalizedEntities(t);
        t = ticketRepo.save(t);

        // Track field changes for notifyUpdated
        List<String> changeDetails = new ArrayList<>();
        if (data.containsKey("title") && !Objects.equals(data.get("title"), prevTitle)) {
            changeDetails.add("Short Description updated from '" + (prevTitle != null ? prevTitle : "none") + "' to '" + data.get("title") + "'");
        }
        if (data.containsKey("description") && !Objects.equals(data.get("description"), prevDesc)) {
            changeDetails.add("Description updated");
        }
        if (data.containsKey("category") && !Objects.equals(data.get("category"), prevCategory)) {
            changeDetails.add("Category updated from '" + (prevCategory != null ? prevCategory : "none") + "' to '" + data.get("category") + "'");
        }
        if (data.containsKey("subcategory") && !Objects.equals(data.get("subcategory"), prevSubcat)) {
            changeDetails.add("Subcategory updated from '" + (prevSubcat != null ? prevSubcat : "none") + "' to '" + data.get("subcategory") + "'");
        }
        if (data.containsKey("priority") && !Objects.equals(data.get("priority"), prevPriority)) {
            changeDetails.add("Priority updated from '" + (prevPriority != null ? prevPriority : "none") + "' to '" + data.get("priority") + "'");
        }
        if (data.containsKey("assignmentGroup") && !Objects.equals(data.get("assignmentGroup"), prevGroup)) {
            changeDetails.add("Assignment Group updated from '" + (prevGroup != null ? prevGroup : "none") + "' to '" + data.get("assignmentGroup") + "'");
        }

        if (!changeDetails.isEmpty()) {
            emailService.notifyUpdated(t, updatedByName, changeDetails);
        }

        // Activity entry
        String msg = "Ticket updated";
        if (newStatus != null && !newStatus.equals(prevStatus)) msg = "Status changed to " + newStatus;
        else if (data.containsKey("assignedTo") && !Objects.equals(data.get("assignedTo"), prevAssignee)) msg = "Assigned to updated";
        logActivity(t.getId(), "status_change", "public",
            updatedBy != null ? updatedBy : (String) data.getOrDefault("updatedById","System"),
            updatedByName != null ? updatedByName : (String) data.getOrDefault("updatedBy","System"), msg, null);

        // Notifications
        String newStatusLocal = t.getStatus();
        if (newStatusLocal != null && !newStatusLocal.equals(prevStatus)) {
            if (t.getCreatedBy() != null) {
                createNotification(t.getCreatedBy(), "Ticket Status Updated",
                    "Your ticket " + t.getTicketNumber() + " status changed to " + newStatusLocal,
                    "status_changed", t.getTicketNumber());
            }

            emailService.notifyStatusChanged(t, prevStatus, newStatusLocal, updatedByName);
            if ("Resolved".equalsIgnoreCase(newStatusLocal)) {
                emailService.notifyResolved(t, updatedByName);
            } else if ("Closed".equalsIgnoreCase(newStatusLocal)) {
                emailService.notifyClosed(t, updatedByName);
            } else if (List.of("Resolved", "Closed").contains(prevStatus) && !List.of("Resolved", "Closed").contains(newStatusLocal)) {
                emailService.notifyReopened(t, updatedByName);
            }
        }

        String newAssignee = t.getAssignedTo();
        if (newAssignee != null && !newAssignee.equals(prevAssignee)) {
            createNotification(newAssignee, "Ticket Assigned to You",
                "Ticket " + t.getTicketNumber() + " has been assigned to you.",
                "ticket_assigned", t.getTicketNumber());

            emailService.notifyAssigned(t, updatedByName);
        }

        // Approval requested check
        String newApprovalStatus = t.getApprovalStatus();
        if (newApprovalStatus != null && "Requested".equals(newApprovalStatus) && !newApprovalStatus.equals(prevApprovalStatus)) {
            String approverEmail = (String) data.get("approverEmail");
            String approverName = (String) data.get("approverName");
            if (approverEmail == null || approverEmail.isBlank()) {
                // Look up group manager
                String groupName = t.getAssignmentGroup();
                if (groupName != null && !groupName.isBlank()) {
                    try {
                        Map<String, Object> groupInfo = jdbcTemplate.queryForMap(
                            "SELECT manager_uid, manager_name, assignment_email FROM settings_groups WHERE name = ? LIMIT 1", groupName);
                        String mUid = (String) groupInfo.get("manager_uid");
                        String mName = (String) groupInfo.get("manager_name");
                        String mEmail = (String) groupInfo.get("assignment_email");
                        if (mEmail != null && mEmail.contains("@")) {
                            approverEmail = mEmail;
                            approverName = mName != null ? mName : "Group Manager";
                        } else if (mUid != null) {
                            approverEmail = userRepo.findByUid(mUid).map(User::getEmail).orElse(null);
                            approverName = mName != null ? mName : (approverEmail != null ? approverEmail : "Group Manager");
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (approverEmail == null || approverEmail.isBlank()) {
                approverEmail = userRepo.findByRoleInAndIsActiveTrue(List.of("admin", "super_admin", "ultra_super_admin"))
                    .stream().findFirst().map(User::getEmail).orElse("admin@technosprint.net");
                approverName = "System Administrator";
            }
            emailService.notifyApprovalRequested(t, approverEmail, approverName);
        }

        // Escalation check (priority becomes "1 - Critical" and was not before)
        String newPriority = t.getPriority();
        if (newPriority != null && "1 - Critical".equals(newPriority) && !newPriority.equals(prevPriority)) {
            // Notify manager of group
            String groupName = t.getAssignmentGroup();
            String managerEmail = null;
            String managerName = null;
            if (groupName != null && !groupName.isBlank()) {
                try {
                    Map<String, Object> groupInfo = jdbcTemplate.queryForMap(
                        "SELECT manager_uid, manager_name, assignment_email FROM settings_groups WHERE name = ? LIMIT 1", groupName);
                    String mUid = (String) groupInfo.get("manager_uid");
                    String mName = (String) groupInfo.get("manager_name");
                    String mEmail = (String) groupInfo.get("assignment_email");
                    if (mEmail != null && mEmail.contains("@")) {
                        managerEmail = mEmail;
                        managerName = mName != null ? mName : "Group Manager";
                    } else if (mUid != null) {
                        managerEmail = userRepo.findByUid(mUid).map(User::getEmail).orElse(null);
                        managerName = mName != null ? mName : "Group Manager";
                    }
                } catch (Exception ignored) {}
            }
            
            if (managerEmail != null && managerEmail.contains("@")) {
                emailService.notifyEscalated(t, managerEmail, managerName, "Ticket escalated to Critical priority");
            } else {
                // Fallback to notify first admin
                String adminEmail = userRepo.findByRoleInAndIsActiveTrue(List.of("admin", "super_admin", "ultra_super_admin"))
                    .stream().findFirst().map(User::getEmail).orElse("admin@technosprint.net");
                emailService.notifyEscalated(t, adminEmail, "System Administrator", "Ticket escalated to Critical priority");
            }
            
            // Also notify assigned agent if any
            String agentEmail = "";
            if (t.getAssignedTo() != null) {
                agentEmail = userRepo.findByUid(t.getAssignedTo())
                    .map(User::getEmail)
                    .orElse(t.getAssignedTo());
            }
            if (isEmail(agentEmail)) {
                emailService.notifyEscalated(t, agentEmail, t.getAssignedToName() != null ? t.getAssignedToName() : "Assigned Agent", "Ticket escalated to Critical priority");
            }
        }

        return t;
    }

    // ── Activities ─────────────────────────────────────────────────────────────
    public List<TicketActivity> getActivities(Long ticketId, String visibility, List<String> types) {
        if (visibility != null) {
            return activityRepo.findByTicketIdAndVisibilityTypeOrderByCreatedAtAsc(ticketId, visibility);
        }
        if (types != null && !types.isEmpty()) {
            return activityRepo.findByTicketIdAndActivityTypeInOrderByCreatedAtAsc(ticketId, types);
        }
        return activityRepo.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public TicketActivity addActivity(Long ticketId, Map<String,Object> data) {
        String actType = (String) data.getOrDefault("activity_type", "comment");
        String visType = (String) data.getOrDefault("visibility_type",
            "work_note".equals(actType) ? "internal" : "public");

        TicketActivity a = TicketActivity.builder()
            .ticketId(ticketId)
            .activityType(actType)
            .visibilityType(visType)
            .channel((String) data.getOrDefault("channel","portal"))
            .messageId((String) data.get("message_id"))
            .threadId((String) data.get("thread_id"))
            .createdBy((String) data.getOrDefault("created_by","System"))
            .createdByName((String) data.getOrDefault("created_by_name","System"))
            .message(((String) data.get("message")).trim())
            .metadataJson(data.get("metadata_json") != null ? data.get("metadata_json").toString() : null)
            .build();

        // Update ticket timestamp + auto-complete Response SLA on first public comment
        ticketRepo.findById(ticketId).ifPresent(t -> {
            t.setUpdatedAt(LocalDateTime.now());

            // If this is the FIRST public comment/response, mark Response SLA as Completed
            boolean isPublicReply = "public".equals(visType)
                && ("comment".equals(actType) || "work_note".equals(actType));
            if (isPublicReply && t.getFirstResponseAt() == null
                && !List.of("Resolved","Closed","Canceled").contains(t.getStatus())) {
                t.setFirstResponseAt(LocalDateTime.now());
                if (!"Breached".equals(t.getResponseSlaStatus())) {
                    t.setResponseSlaStatus("Completed");
                }
            }

            ticketRepo.save(t);

            // Queue outbound email ONLY if explicitly requested via email_note flag
            boolean emailNoteOption = Boolean.TRUE.equals(data.get("email_note")) 
                || Boolean.TRUE.equals(data.get("emailNote"))
                || Boolean.TRUE.equals(data.get("send_email"))
                || Boolean.TRUE.equals(data.get("sendEmail"));
            
            if (emailNoteOption) {
                try {
                    emailService.notifyCommentAdded(t, a);
                } catch (Exception emailEx) {
                    // Email failure must NOT prevent the activity from being saved
                    log.warn("[TicketService] Email notification failed for ticket {} (non-critical): {}", ticketId, emailEx.getMessage());
                }
            }
        });

        return activityRepo.save(a);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private void logActivity(Long ticketId, String type, String vis, String by, String byName, String msg, String meta) {
        activityRepo.save(TicketActivity.builder()
            .ticketId(ticketId).activityType(type).visibilityType(vis)
            .createdBy(by).createdByName(byName).message(msg).metadataJson(meta)
            .channel("system").build());
    }

    private void createNotification(String userId, String title, String message, String type, String ticketId) {
        notifRepo.save(Notification.builder()
            .userId(userId).title(title).message(message).type(type).ticketId(ticketId)
            .isRead(false).build());
    }

    private void sendCreateNotifications(Ticket t, Map<String,Object> data) {
        String createdBy = (String) data.get("createdBy");
        String assignedTo = (String) data.get("assignedTo");
        if (createdBy != null) createNotification(createdBy, "Ticket Created Successfully",
            "Ticket ID: " + t.getTicketNumber(), "ticket_created", t.getTicketNumber());
        if (assignedTo != null) {
            createNotification(assignedTo, "A ticket has been assigned to you",
                "Ticket ID: " + t.getTicketNumber(), "ticket_assigned", t.getTicketNumber());
        } else {
            userRepo.findByRoleInAndIsActiveTrue(
                List.of("agent","admin","super_admin","ultra_super_admin")
            ).forEach(u -> createNotification(u.getUid(), "New Unassigned Ticket",
                t.getCreatedByName() + " created ticket " + t.getTicketNumber(),
                "ticket_unassigned", t.getTicketNumber()));
        }
    }

    private int calcPoints(Ticket t) {
        if (t.getResolutionDeadline() == null) return 5;
        long deadline   = t.getResolutionDeadline().toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
        long createdAt  = t.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
        long now        = System.currentTimeMillis();
        if (now < deadline) {
            long total  = deadline - createdAt;
            long saved  = deadline - now;
            return Math.max(10, (int) Math.round(((double) saved / total) * 100));
        }
        return 5;
    }

    private void validateRcaIfBreached(Ticket t, Map<String,Object> data) {
        boolean breached = false;
        if (t.getResolutionDeadline() != null && t.getResolvedAt() == null) {
            breached = LocalDateTime.now().isAfter(t.getResolutionDeadline());
        }
        if (!breached) return;

        Object metaObj = data.get("slaDelayMeta");
        if (metaObj == null) {
            metaObj = data.get("sla_delay_meta_json");
        }
        if (metaObj == null) throw new RuntimeException(
            "SLA Breach Root Cause Analysis (RCA) is mandatory before resolving a breached ticket.");

        Map<String, Object> meta = null;
        if (metaObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) metaObj;
            meta = casted;
        } else if (metaObj instanceof String) {
            try {
                meta = new com.fasterxml.jackson.databind.ObjectMapper().readValue((String) metaObj, Map.class);
            } catch (Exception ignored) {}
        }

        if (meta == null) throw new RuntimeException(
            "SLA Breach Root Cause Analysis (RCA) is mandatory before resolving a breached ticket.");
        boolean hasRca = meta.get("rootCauseAnalysis") != null &&
            !meta.get("rootCauseAnalysis").toString().isBlank();
        if (!hasRca) throw new RuntimeException(
            "SLA Breach Root Cause Analysis (RCA) is mandatory before resolving a breached ticket.");
    }

    @SuppressWarnings("unchecked")
    private void apply(Ticket t, Map<String,Object> data, boolean adminAccess) {
        if (data.containsKey("caller"))            t.setCaller((String) data.get("caller"));
        if (data.containsKey("callerEmail"))       t.setCallerEmail((String) data.get("callerEmail"));
        if (data.containsKey("companyId") || data.containsKey("company_id")) {
            Object rawCompanyId = data.containsKey("companyId") ? data.get("companyId") : data.get("company_id");
            Long companyId = null;
            if (rawCompanyId instanceof Number) {
                companyId = ((Number) rawCompanyId).longValue();
            } else if (rawCompanyId instanceof String && !((String) rawCompanyId).isBlank()) {
                try {
                    companyId = Long.parseLong((String) rawCompanyId);
                } catch (NumberFormatException ignored) {}
            }
            t.setCompanyId(companyId);
        }
        if (data.containsKey("category"))          t.setCategory((String) data.get("category"));
        if (adminAccess && data.containsKey("incidentCategory")) t.setIncidentCategory((String) data.get("incidentCategory"));
        if (data.containsKey("title"))             t.setTitle((String) data.get("title"));
        if (data.containsKey("description"))       t.setDescription((String) data.get("description"));
        if (data.containsKey("status"))            t.setStatus((String) data.get("status"));
        if (data.containsKey("priority"))          t.setPriority((String) data.get("priority"));
        if (data.containsKey("impact"))            t.setImpact((String) data.get("impact"));
        if (data.containsKey("urgency"))           t.setUrgency((String) data.get("urgency"));
        if (data.containsKey("channel"))           t.setChannel((String) data.get("channel"));
        if (data.containsKey("subcategory"))       t.setSubcategory((String) data.get("subcategory"));
        if (data.containsKey("service"))           t.setService((String) data.get("service"));
        if (data.containsKey("serviceOffering"))   t.setServiceOffering((String) data.get("serviceOffering"));
        if (data.containsKey("cmdbItem"))          t.setCmdbItem((String) data.get("cmdbItem"));
        if (data.containsKey("assignmentGroup"))   t.setAssignmentGroup((String) data.get("assignmentGroup"));
        if (data.containsKey("assignedTo"))        t.setAssignedTo((String) data.get("assignedTo"));
        if (data.containsKey("assignedToName"))    t.setAssignedToName((String) data.get("assignedToName"));
        if (data.containsKey("responseSlaStatus")) t.setResponseSlaStatus((String) data.get("responseSlaStatus"));
        if (data.containsKey("resolutionSlaStatus")) t.setResolutionSlaStatus((String) data.get("resolutionSlaStatus"));
        if (data.containsKey("onHoldReason"))      t.setOnHoldReason((String) data.get("onHoldReason"));
        if (data.containsKey("approvalStatus"))    t.setApprovalStatus((String) data.get("approvalStatus"));
        if (data.containsKey("resolutionCode"))    t.setResolutionCode((String) data.get("resolutionCode"));
        if (data.containsKey("resolutionNotes"))   t.setResolutionNotes((String) data.get("resolutionNotes"));
        if (data.containsKey("resolutionMethod"))  t.setResolutionMethod((String) data.get("resolutionMethod"));
        if (data.containsKey("closureReason"))     t.setClosureReason((String) data.get("closureReason"));
        if (data.containsKey("resolvedBy"))        t.setResolvedBy((String) data.get("resolvedBy"));
        // SLA delay metadata (JSON strings)
        if (data.containsKey("slaDelayMeta") || data.containsKey("sla_delay_meta_json")) {
            Object meta = data.containsKey("slaDelayMeta") ? data.get("slaDelayMeta") : data.get("sla_delay_meta_json");
            if (meta instanceof Map || meta instanceof List) {
                try {
                    t.setSlaDelayMetaJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(meta));
                } catch (Exception e) {
                    t.setSlaDelayMetaJson(meta.toString());
                }
            } else {
                t.setSlaDelayMetaJson(meta != null ? meta.toString() : null);
            }
        }
        if (data.containsKey("slaDelayLogs") || data.containsKey("sla_delay_logs_json")) {
            Object logs = data.containsKey("slaDelayLogs") ? data.get("slaDelayLogs") : data.get("sla_delay_logs_json");
            if (logs instanceof Map || logs instanceof List) {
                try {
                    t.setSlaDelayLogsJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(logs));
                } catch (Exception e) {
                    t.setSlaDelayLogsJson(logs.toString());
                }
            } else {
                t.setSlaDelayLogsJson(logs != null ? logs.toString() : "[]");
            }
        }
        // Paused time tracking
        if (data.containsKey("totalPausedTime") || data.containsKey("totalPausedTimeMs")) {
            Object val = data.containsKey("totalPausedTimeMs") ? data.get("totalPausedTimeMs") : data.get("totalPausedTime");
            if (val instanceof Number) t.setTotalPausedTimeMs(((Number) val).longValue());
        }
        // On-hold start (ISO string or null)
        if (data.containsKey("onHoldStart")) {
            Object ohs = data.get("onHoldStart");
            if (ohs == null) { t.setOnHoldStart(null); }
            else if (ohs instanceof String && !((String) ohs).isBlank()) {
                try { t.setOnHoldStart(LocalDateTime.parse((String) ohs)); } catch (Exception ignored) {}
            }
        }
        // First response tracking
        if (data.containsKey("firstResponseAt")) {
            Object fra = data.get("firstResponseAt");
            if (fra == null) { t.setFirstResponseAt(null); }
            else if (fra instanceof String && !((String) fra).isBlank()) {
                try { t.setFirstResponseAt(LocalDateTime.parse((String) fra)); } catch (Exception ignored) {}
            }
        }
        if (data.containsKey("watchList"))           t.setWatchList((String) data.get("watchList"));
        if (data.containsKey("watch_list"))          t.setWatchList((String) data.get("watch_list"));
    }

    private boolean isEmail(String s) { return s != null && s.contains("@"); }

    private void resolveNormalizedEntities(Ticket t) {
        if (t.getCategory() != null && !t.getCategory().isBlank()) {
            t.setCategoryEntity(categoryRepo.findByName(t.getCategory()).orElseGet(() -> 
                categoryRepo.save(Category.builder().name(t.getCategory()).build())
            ));
        } else {
            t.setCategoryEntity(null);
        }

        if (t.getAssignmentGroup() != null && !t.getAssignmentGroup().isBlank()) {
            t.setAssignmentGroupEntity(groupRepo.findByName(t.getAssignmentGroup()).orElseGet(() -> 
                groupRepo.save(AssignmentGroup.builder().name(t.getAssignmentGroup()).build())
            ));
        } else {
            t.setAssignmentGroupEntity(null);
        }

        if (t.getAssignedTo() != null && !t.getAssignedTo().isBlank()) {
            t.setAssignedToUser(userRepo.findByUid(t.getAssignedTo())
                .orElseGet(() -> userRepo.findByEmail(t.getAssignedTo()).orElse(null)));
        } else {
            t.setAssignedToUser(null);
        }

        if (t.getCreatedBy() != null && !t.getCreatedBy().isBlank()) {
            t.setCreatedByUser(userRepo.findByUid(t.getCreatedBy())
                .orElseGet(() -> userRepo.findByEmail(t.getCreatedBy()).orElse(null)));
        } else {
            t.setCreatedByUser(null);
        }
    }
}

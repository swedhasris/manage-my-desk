package com.connectit.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "assets")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Asset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false)                               private String name;
    @Column(length = 50)                                    private String type = "Hardware";
    @Column(length = 50)                                    private String status = "Operational";
    @Column(length = 128)                                   private String owner;
    @Column(name = "owner_name")                            private String ownerName;
    @Column(length = 255)                                   private String location;
    @Column(name = "serial_number",  length = 255)          private String serialNumber;
    @Column(length = 255)                                   private String model;
    @Column(length = 255)                                   private String manufacturer;
    @Column(name = "purchase_date")                         private LocalDate purchaseDate;
    @Column(name = "warranty_expiry")                       private LocalDate warrantyExpiry;
    @Column(name = "ip_address",     length = 50)           private String ipAddress;
    @Column(columnDefinition = "TEXT")                      private String description;
    @Column(name = "created_at")                            private LocalDateTime createdAt;
    @Column(name = "updated_at")                            private LocalDateTime updatedAt;
    @PrePersist void prePersist()  { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate  void preUpdate()   { updatedAt = LocalDateTime.now(); }
}

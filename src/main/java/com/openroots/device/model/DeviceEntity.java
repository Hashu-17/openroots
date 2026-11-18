package com.openroots.device.model;

import com.openroots.project.model.ProjectEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "device")
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "device_type", nullable = false, length = 100)
    private String type;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SensorEntity> sensors = new ArrayList<>();

    protected DeviceEntity() {
    }

    public DeviceEntity(ProjectEntity project, String name, String type) {
        this.project = project;
        this.name = name;
        this.type = type;
        this.status = "offline";
    }

    @PrePersist
    void setRegisteredAt() {
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }

    public void addSensor(SensorEntity sensor) {
        sensors.add(sensor);
        sensor.setDevice(this);
    }

    public UUID getId() {
        return id;
    }

    public ProjectEntity getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public void markOnline(Instant seenAt) {
        status = "online";
        lastSeenAt = seenAt;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public List<SensorEntity> getSensors() {
        return List.copyOf(sensors);
    }
}
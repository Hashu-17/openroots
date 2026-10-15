package com.openroots.device.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "sensor")
public class SensorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "channel_name", nullable = false, length = 255)
    private String channelName;

    @Column(length = 50)
    private String unit;

    protected SensorEntity() {
    }

    public SensorEntity(String channelName, String unit) {
        this.channelName = channelName;
        this.unit = unit;
    }

    void setDevice(DeviceEntity device) {
        this.device = device;
    }

    public UUID getId() {
        return id;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getUnit() {
        return unit;
    }
}
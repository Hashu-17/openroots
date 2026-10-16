package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	BrokerURL          string
	MQTTUsername       string
	MQTTPassword       string
	DatabaseHost       string
	DatabasePort       string
	DatabaseName       string
	DatabaseUser       string
	DatabasePassword   string
	RegistryURL        string
	RegistryAPIKey     string
	HTTPAddr           string
	RegistrySyncEvery  time.Duration
	RegistryWaitForNew time.Duration
	QueueCapacity      int
	WorkerCount        int
}

func Load() (Config, error) {
	cfg := Config{
		BrokerURL:          os.Getenv("MQTT_BROKER_URL"),
		MQTTUsername:       os.Getenv("MQTT_USERNAME"),
		MQTTPassword:       os.Getenv("MQTT_PASSWORD"),
		DatabaseHost:       os.Getenv("DATABASE_HOST"),
		DatabasePort:       valueOr("DATABASE_PORT", "5432"),
		DatabaseName:       os.Getenv("DATABASE_NAME"),
		DatabaseUser:       os.Getenv("DATABASE_USER"),
		DatabasePassword:   os.Getenv("DATABASE_PASSWORD"),
		RegistryURL:        os.Getenv("REGISTRY_URL"),
		RegistryAPIKey:     os.Getenv("REGISTRY_API_KEY"),
		HTTPAddr:           valueOr("INGESTION_HTTP_ADDR", ":8081"),
		RegistrySyncEvery:  3 * time.Second,
		RegistryWaitForNew: 10 * time.Second,
		QueueCapacity:      256,
		WorkerCount:        4,
	}

	for name, val := range map[string]string{
		"MQTT_BROKER_URL":    cfg.BrokerURL,
		"MQTT_USERNAME":      cfg.MQTTUsername,
		"MQTT_PASSWORD":      cfg.MQTTPassword,
		"DATABASE_HOST":      cfg.DatabaseHost,
		"DATABASE_NAME":      cfg.DatabaseName,
		"DATABASE_USER":      cfg.DatabaseUser,
		"DATABASE_PASSWORD":  cfg.DatabasePassword,
		"REGISTRY_URL":       cfg.RegistryURL,
		"REGISTRY_API_KEY":   cfg.RegistryAPIKey,
	} {
		if val == "" {
			return Config{}, fmt.Errorf("%s is required", name)
		}
	}

	var err error
	if cfg.RegistrySyncEvery, err = durationOr("REGISTRY_SYNC_INTERVAL", cfg.RegistrySyncEvery); err != nil {
		return Config{}, err
	}
	if cfg.RegistryWaitForNew, err = durationOr("REGISTRY_NEW_DEVICE_WAIT", cfg.RegistryWaitForNew); err != nil {
		return Config{}, err
	}
	if cfg.QueueCapacity, err = intOr("MQTT_QUEUE_CAPACITY", cfg.QueueCapacity); err != nil {
		return Config{}, err
	}
	if cfg.WorkerCount, err = intOr("INGESTION_WORKERS", cfg.WorkerCount); err != nil {
		return Config{}, err
	}
	if cfg.QueueCapacity < 1 || cfg.WorkerCount < 1 {
		return Config{}, fmt.Errorf("MQTT_QUEUE_CAPACITY and INGESTION_WORKERS must be positive")
	}
	return cfg, nil
}

func valueOr(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func durationOr(name string, fallback time.Duration) (time.Duration, error) {
	value := os.Getenv(name)
	if value == "" {
		return fallback, nil
	}
	duration, err := time.ParseDuration(value)
	if err != nil || duration <= 0 {
		return 0, fmt.Errorf("%s must be a positive Go duration", name)
	}
	return duration, nil
}

func intOr(name string, fallback int) (int, error) {
	value := os.Getenv(name)
	if value == "" {
		return fallback, nil
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return 0, fmt.Errorf("%s must be an integer", name)
	}
	return parsed, nil
}

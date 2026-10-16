package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/openroots/ingestion/internal/config"
	"github.com/openroots/ingestion/internal/consumer"
	"github.com/openroots/ingestion/internal/registry"
	"github.com/openroots/ingestion/internal/storage"
)

func main() {
	logger := log.New(os.Stdout, "ingestion: ", log.LstdFlags|log.LUTC)
	if err := run(logger); err != nil {
		logger.Printf("service stopped: %v", err)
		os.Exit(1)
	}
}

func run(logger *log.Logger) error {
	settings, err := config.Load()
	if err != nil {
		return err
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	deviceRegistry := registry.New(settings.RegistryURL, settings.RegistryAPIKey)
	for {
		if err := deviceRegistry.Refresh(ctx); err == nil {
			logger.Printf("loaded registered device and sensor schemas")
			break
		} else if ctx.Err() != nil {
			return nil
		} else {
			logger.Printf("waiting for Spring device registry: %v", err)
		}
		if err := pause(ctx, settings.RegistrySyncEvery); err != nil {
			return nil
		}
	}
	go deviceRegistry.Run(ctx, settings.RegistrySyncEvery, logger)

	database, err := storage.New(
		ctx,
		settings.DatabaseHost,
		settings.DatabasePort,
		settings.DatabaseName,
		settings.DatabaseUser,
		settings.DatabasePassword,
		settings.WorkerCount,
	)
	if err != nil {
		return err
	}
	defer database.Close()

	mqttConsumer := consumer.New(settings, deviceRegistry, database, logger)
	defer mqttConsumer.Close()
	if err := mqttConsumer.Start(ctx); err != nil {
		stop()
		return err
	}
	logger.Printf("Go telemetry ingestion is running with %d workers and queue capacity %d", settings.WorkerCount, settings.QueueCapacity)

	select {
	case <-ctx.Done():
		return nil
	case err := <-mqttConsumer.Fatal():
		stop()
		return err
	}
}

func pause(ctx context.Context, duration time.Duration) error {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

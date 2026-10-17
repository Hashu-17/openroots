package consumer

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"
	"github.com/openroots/ingestion/internal/config"
	"github.com/openroots/ingestion/internal/live"
	"github.com/openroots/ingestion/internal/registry"
	"github.com/openroots/ingestion/internal/storage"
	"github.com/openroots/ingestion/internal/telemetry"
)

const subscriptionTopic = "openroots/v1/devices/+/telemetry"

type Consumer struct {
	client    mqtt.Client
	queue     chan mqtt.Message
	ctx       context.Context
	registry  *registry.Registry
	store     *storage.Store
	publisher *live.Hub
	settings  config.Config
	logger    *log.Logger
	fatal     chan error
	workers   int
	workerWG  sync.WaitGroup
}

func New(
	settings config.Config,
	deviceRegistry *registry.Registry,
	store *storage.Store,
	publisher *live.Hub,
	logger *log.Logger,
) *Consumer {
	consumer := &Consumer{
		queue:     make(chan mqtt.Message, settings.QueueCapacity),
		registry:  deviceRegistry,
		store:     store,
		publisher: publisher,
		settings:  settings,
		logger:    logger,
		fatal:     make(chan error, 1),
		workers:   settings.WorkerCount,
	}

	options := mqtt.NewClientOptions().
		AddBroker(settings.BrokerURL).
		SetClientID("openroots-ingest-v1").
		SetUsername(settings.MQTTUsername).
		SetPassword(settings.MQTTPassword).
		SetCleanSession(false).
		SetResumeSubs(true).
		SetAutoAckDisabled(true).
		SetOrderMatters(false).
		SetKeepAlive(20 * time.Second).
		SetConnectTimeout(10 * time.Second).
		SetMaxReconnectInterval(30 * time.Second)
	options.SetConnectionLostHandler(func(_ mqtt.Client, err error) {
		logger.Printf("MQTT connection lost: %v", err)
	})
	options.SetOnConnectHandler(func(client mqtt.Client) {
		token := client.Subscribe(subscriptionTopic, 1, consumer.enqueue)
		if !token.WaitTimeout(15 * time.Second) {
			consumer.reportFatal(fmt.Errorf("timed out subscribing to %s", subscriptionTopic))
			return
		}
		if err := token.Error(); err != nil {
			consumer.reportFatal(fmt.Errorf("subscribe to telemetry topic: %w", err))
			return
		}
		logger.Printf("subscribed to %s at QoS 1", subscriptionTopic)
	})
	consumer.client = mqtt.NewClient(options)
	return consumer
}

func (c *Consumer) Start(ctx context.Context) error {
	c.ctx = ctx
	for workerID := 0; workerID < c.workers; workerID++ {
		c.workerWG.Add(1)
		go func(id int) {
			defer c.workerWG.Done()
			c.worker(ctx, id)
		}(workerID + 1)
	}

	token := c.client.Connect()
	if !token.WaitTimeout(30 * time.Second) {
		return fmt.Errorf("timed out connecting to MQTT broker")
	}
	if err := token.Error(); err != nil {
		return fmt.Errorf("connect to MQTT broker: %w", err)
	}
	return nil
}

func (c *Consumer) Fatal() <-chan error {
	return c.fatal
}

func (c *Consumer) Close() {
	if c.client != nil && c.client.IsConnected() {
		c.client.Disconnect(250)
	}
	c.workerWG.Wait()
}

func (c *Consumer) enqueue(_ mqtt.Client, message mqtt.Message) {
	select {
	case c.queue <- message:
	case <-c.ctx.Done():
		// Leave the message unacknowledged so the persistent broker session can
		// redeliver it after the ingestion service starts again.
	}
}

func (c *Consumer) worker(ctx context.Context, workerID int) {
	for {
		select {
		case <-ctx.Done():
			return
		case message := <-c.queue:
			if ctx.Err() != nil {
				return
			}
			if err := c.process(ctx, message); err != nil {
				c.logger.Printf("worker %d cannot persist MQTT message %d: %v", workerID, message.MessageID(), err)
				c.reportFatal(err)
				return
			}
		}
	}
}

func (c *Consumer) process(parent context.Context, message mqtt.Message) error {
	if message.Retained() {
		c.logger.Printf("discarding retained telemetry message on %s", message.Topic())
		message.Ack()
		return nil
	}

	deviceID, err := telemetry.DeviceIDFromTopic(message.Topic())
	if err != nil {
		c.logger.Printf("discarding MQTT message %d: %v", message.MessageID(), err)
		message.Ack()
		return nil
	}

	ctx, cancel := context.WithTimeout(parent, 20*time.Second)
	defer cancel()

	schema, exists := c.registry.Device(deviceID)
	if !exists {
		// Registration commits in Spring before Go's next snapshot refresh.
		// Wait briefly for that propagation before treating the topic as unknown.
		schema, exists = c.registry.WaitForDevice(ctx, deviceID, c.settings.RegistryWaitForNew)
	}
	if !exists {
		c.logger.Printf("discarding telemetry from unregistered or revoked device %s", deviceID)
		message.Ack()
		return nil
	}

	readings, err := telemetry.Validate(message.Payload(), schema)
	if err != nil {
		c.logger.Printf("discarding invalid telemetry for device %s: %v", deviceID, err)
		message.Ack()
		return nil
	}

	if err := c.writeWithRetry(ctx, readings); err != nil {
		// Do not ack on storage failure. Exiting causes broker session redelivery,
		// and the database upsert makes already committed retries idempotent.
		return err
	}
	c.publisher.Publish(deviceID, readings)
	message.Ack()
	return nil
}

func (c *Consumer) writeWithRetry(ctx context.Context, readings []telemetry.Reading) error {
	backoffs := []time.Duration{250 * time.Millisecond, 750 * time.Millisecond, 1500 * time.Millisecond}
	var lastErr error
	for attempt := 0; attempt <= len(backoffs); attempt++ {
		if err := c.store.InsertReadings(ctx, readings); err == nil {
			return nil
		} else {
			lastErr = err
		}
		if attempt == len(backoffs) {
			break
		}
		timer := time.NewTimer(backoffs[attempt])
		select {
		case <-ctx.Done():
			timer.Stop()
			return ctx.Err()
		case <-timer.C:
		}
	}
	return fmt.Errorf("database retries exhausted: %w", lastErr)
}

func (c *Consumer) reportFatal(err error) {
	select {
	case c.fatal <- err:
	default:
	}
}

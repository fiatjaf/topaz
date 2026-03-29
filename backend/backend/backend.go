package backend

import (
	"context"
	"fmt"
	"log"
	"time"

	"fiatjaf.com/nostr"
	"fiatjaf.com/nostr/eventstore/mmm"
	"fiatjaf.com/nostr/khatru"
	"fiatjaf.com/nostr/nip11"
	"iter"
)

var relay *khatru.Relay
var mmmDB *mmm.MultiMmapManager
var mainIndex *mmm.IndexingLayer

func StartRelay(port string) error {
	relay = khatru.NewRelay()

	// Initialize MMM database
	mmmDB = &mmm.MultiMmapManager{}
	err := mmmDB.Init()
	if err != nil {
		return fmt.Errorf("failed to initialize mmm: %w", err)
	}

	// Create the "main" indexing layer
	mainIndex, err = mmmDB.EnsureLayer("main")
	if err != nil {
		return fmt.Errorf("failed to create main indexer: %w", err)
	}

	// Attach MMM to Khatru as the event store (with context wrappers)
	relay.StoreEvent = func(ctx context.Context, event nostr.Event) error {
		return mainIndex.SaveEvent(event)
	}
	relay.QueryStored = func(ctx context.Context, filter nostr.Filter) iter.Seq[nostr.Event] {
		return func(yield func(nostr.Event) bool) {
			seq := mainIndex.QueryEvents(filter, 100)
			for event := range seq {
				if !yield(event) {
					return
				}
			}
		}
	}
	relay.ReplaceEvent = func(ctx context.Context, event nostr.Event) error {
		return mainIndex.ReplaceEvent(event)
	}
	relay.DeleteEvent = func(ctx context.Context, id nostr.ID) error {
		return mainIndex.DeleteEvent(id)
	}

	// Set up policies - simplified OnConnect
	relay.OnConnect = func(ctx context.Context) {
		log.Printf("Client connected")
	}

	relay.OnEvent = func(ctx context.Context, event nostr.Event) (reject bool, msg string) {
		return false, ""
	}

	relay.OnRequest = func(ctx context.Context, filter nostr.Filter) (reject bool, msg string) {
		return false, ""
	}

	// Configure relay info using NIP-11 document
	relay.Info = &nip11.RelayInformationDocument{
		Name:        "Topaz Relay",
		Description: "A Nostr relay built with Khatru and MMM",
		Contact:     "",
	}

	// Start the relay server - note: this may not work well on Android via gomobile
	go func() {
		log.Printf("Starting relay on port %s", port)
		err := relay.Start("", 3334)
		if err != nil {
			log.Printf("relay error: %v", err)
		}
	}()

	time.Sleep(100 * time.Millisecond)
	log.Printf("Relay started on port %s", port)
	return nil
}

func StopRelay() error {
	if relay != nil {
		log.Println("Relay stopping...")
	}
	if mmmDB != nil {
		mmmDB.Close()
	}
	return nil
}

// GetMessage returns a string message
func GetMessage() string {
	if relay != nil && relay.Info != nil {
		return fmt.Sprintf("Relay running! Name: %s", relay.Info.Name)
	}
	return "Relay not started. Call StartRelay first."
}

// GetRelayStatus returns status information
func GetRelayStatus() string {
	if relay == nil {
		return "Relay not initialized"
	}
	if relay.Info != nil {
		return fmt.Sprintf("Relay '%s' is running", relay.Info.Name)
	}
	return "Relay is running"
}
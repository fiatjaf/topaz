package backend

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"os"
	"time"

	"fiatjaf.com/nostr/eventstore/mmm"
	"fiatjaf.com/nostr/khatru"
	"fiatjaf.com/nostr/nip11"
	"github.com/rs/zerolog"
	"golang.org/x/sync/errgroup"
)

var (
	relay     *khatru.Relay
	mmmm      *mmm.MultiMmapManager
	mainIndex *mmm.IndexingLayer
	log       = zerolog.New(os.Stderr).Output(zerolog.ConsoleWriter{Out: os.Stdout}).With().Timestamp().Logger()
)

func StartRelay(port string) error {
	ctx := context.Background()
	relay = khatru.NewRelay()

	mmmm = &mmm.MultiMmapManager{}
	err := mmmm.Init()
	if err != nil {
		return fmt.Errorf("failed to initialize mmm: %w", err)
	}

	mainIndex, err = mmmm.EnsureLayer("main")
	if err != nil {
		return fmt.Errorf("failed to create main indexer: %w", err)
	}

	relay.UseEventstore(mainIndex, 500)

	// Configure relay info using NIP-11 document
	relay.Info = &nip11.RelayInformationDocument{
		Name:        "topaz",
		Description: "local relay running on Android",
	}

	server := &http.Server{
		Addr:    "127.0.0.1:" + port,
		Handler: relay,
		ConnContext: func(ctx context.Context, c net.Conn) context.Context {
			return ctx
		},
	}

	g, ctx := errgroup.WithContext(ctx)

	g.Go(server.ListenAndServe)
	log.Info().Msg("running on http://localhost:" + port)

	g.Go(func() error {
		<-ctx.Done()
		if err := server.Shutdown(context.Background()); err != nil {
			return err
		}
		if err := server.Close(); err != nil {
			return err
		}
		return nil
	})

	time.Sleep(100 * time.Millisecond)
	log.Printf("relay started on port %s", port)
	return nil
}

func StopRelay() error {
	if relay != nil {
		log.Println("relay stopping...")
	}
	if mmmm != nil {
		mmmm.Close()
	}
	return nil
}

func GetRelayStatus() string {
	if relay == nil {
		return "relay not initialized"
	}
	if relay.Info != nil {
		return fmt.Sprintf("relay '%s' is running", relay.Info.Name)
	}
	return "relay is running"
}


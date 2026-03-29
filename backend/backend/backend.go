package backend

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"fiatjaf.com/nostr/eventstore/boltdb"
	"fiatjaf.com/nostr/khatru"
	"fiatjaf.com/nostr/nip11"
	"github.com/rs/zerolog"
	_ "golang.org/x/mobile/bind"
	"golang.org/x/sync/errgroup"
)

var (
	relay *khatru.Relay
	db    *boltdb.BoltBackend
	log   = zerolog.New(os.Stderr).Output(zerolog.ConsoleWriter{Out: os.Stdout}).With().Timestamp().Logger()
)

func StartRelay(datadir string, port string) error {
	ctx := context.Background()
	relay = khatru.NewRelay()

	db = &boltdb.BoltBackend{
		Path: filepath.Join(datadir, "nostr.db"),
	}
	err := db.Init()
	if err != nil {
		return fmt.Errorf("failed to initialize boltdb: %w", err)
	}

	relay.UseEventstore(db, 500)

	// Configure relay info using NIP-11 document
	relay.Info = &nip11.RelayInformationDocument{
		Name:        "topaz",
		Description: "local relay running on Android",
	}

	server := &http.Server{
		Addr:    "0.0.0.0:" + port,
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
	if db != nil {
		db.Close()
	}
	return nil
}

func GetRelayStatus() string {
	if relay == nil {
		return "relay not initialized"
	}
	return "relay is running"
}

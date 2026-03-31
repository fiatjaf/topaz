package backend

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"fiatjaf.com/nostr"
	"fiatjaf.com/nostr/eventstore/boltdb"
	"fiatjaf.com/nostr/khatru"
	"fiatjaf.com/nostr/nip11"
	"github.com/rs/zerolog"
	_ "golang.org/x/mobile/bind"
	"golang.org/x/sync/errgroup"
)

var (
	relay         *khatru.Relay
	relayErr      error
	db            *boltdb.BoltBackend
	log           = zerolog.New(os.Stderr).Output(zerolog.ConsoleWriter{Out: os.Stdout}).With().Timestamp().Logger()
	statsCallback StatsCallback
	cancelRelay   context.CancelFunc
)

// StatsCallback is the interface that Kotlin implements to receive stats updates
type StatsCallback interface {
	OnStatsUpdate(eventCount int64, activeSubscriptions int64, connectedClients int64)
}

func StartRelay(datadir string, port string, callback StatsCallback) error {
	ctx := context.Background()
	relay = khatru.NewRelay()
	statsCallback = callback

	db = &boltdb.BoltBackend{
		Path: filepath.Join(datadir, "nostr.db"),
	}
	err := db.Init()
	if err != nil {
		return fmt.Errorf("failed to initialize boltdb: %w", err)
	}

	relay.UseEventstore(db, 500)

	// register hooks for stats
	relay.OnConnect = func(ctx context.Context) {
		go func() {
			time.Sleep(200 * time.Millisecond)
			pushStats()
		}()
	}

	relay.OnDisconnect = func(ctx context.Context) {
		go func() {
			time.Sleep(200 * time.Millisecond)
			pushStats()
		}()
	}

	relay.OnRequest = func(ctx context.Context, filter nostr.Filter) (reject bool, msg string) {
		go func() {
			time.Sleep(200 * time.Millisecond)
			pushStats()
		}()
		return false, ""
	}

	relay.OnEventSaved = func(ctx context.Context, event nostr.Event) {
		pushStats()
	}

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

	relayCtx, cancel := context.WithCancel(ctx)
	g, ctx := errgroup.WithContext(relayCtx)
	cancelRelay = cancel

	g.Go(server.ListenAndServe)
	log.Info().Msg("running on port " + port)

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

	go func() {
		err := g.Wait()
		if err == nil || http.ErrServerClosed == err {
			return
		}
		log.Error().Err(err).Msg("something went wrong")
		relayErr = err
	}()

	return nil
}

func StopRelay() error {
	relayErr = nil
	statsCallback = nil // clear callback before shutdown to prevent crashes
	if relay != nil {
		log.Info().Msg("relay stopping...")
		cancelRelay()
	}
	if db != nil {
		db.Close()
	}
	relay = nil
	return nil
}

func GetRelayStatus() string {
	if relayErr != nil {
		return relayErr.Error()
	}
	if relay == nil {
		return "relay not initialized"
	}
	return "relay is running"
}

func GetRelayURLs(port string) string {
	// URLs are now computed on the Kotlin side
	// This function is kept for backward compatibility
	log.Debug().Msg("GetRelayURLs called - URLs should be computed on Kotlin side")
	return "[]"
}

func pushStats() {
	if statsCallback == nil || relay == nil {
		return
	}
	clients, listeners := relay.Stats()
	count, _ := relay.Count(context.Background(), nostr.Filter{})

	statsCallback.OnStatsUpdate(int64(count), int64(listeners), int64(clients))
	log.Info().Msg("ONSTATSUPDATE CALLED")
}

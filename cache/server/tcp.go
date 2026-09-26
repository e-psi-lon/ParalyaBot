package server

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"paralyabot-cache/cache"
	"paralyabot-cache/executor"
	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
	"runtime/debug"
	"sync/atomic"
	"time"
)

const (
	minAcceptBackoff = 5 * time.Millisecond
	maxAcceptBackoff = 1 * time.Second
)

func nextAcceptBackoff(current time.Duration) time.Duration {
	if current <= 0 {
		return minAcceptBackoff
	}
	next := current * 2
	if next > maxAcceptBackoff || next <= 0 { // guard against overflow
		return maxAcceptBackoff
	}
	return next
}

func Serve(ctx context.Context, c *cache.Cache, address string) error {
	listener, err := (&net.ListenConfig{}).Listen(ctx, "tcp", address)
	if err != nil {
		log.Printf("failed to listen on %s: %v", address, err)
		return err
	}
	defer func(listener net.Listener) {
		closeErr := listener.Close()
		if closeErr != nil {
			log.Printf("error closing listener: %v", closeErr)
		}
	}(listener)
	go func() {
		<-ctx.Done()
		if closeErr := listener.Close(); closeErr != nil && !errors.Is(closeErr, net.ErrClosed) {
			log.Printf("error closing listener: %v", closeErr)
		}
	}()
	log.Printf("cache server listening on %s", listener.Addr())
	clients := executor.NewClients()
	var ids atomic.Int64
	var acceptErrBackoff time.Duration

	for {
		connection, err := listener.Accept()
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			acceptErrBackoff = nextAcceptBackoff(acceptErrBackoff)
			log.Printf("accept error: %v, backing off %s", err, acceptErrBackoff)
			time.Sleep(acceptErrBackoff)
			continue
		}
		acceptErrBackoff = 0
		go handleConnection(connection, c, clients, &ids)
	}
}

func handleConnection(connection net.Conn, c *cache.Cache, clients *executor.Clients, ids *atomic.Int64) {
	id := ids.Add(1)
	defer func() {
		if recovered := recover(); recovered != nil {
			log.Printf("connection %d recovered panic: %v", id, recovered)
			log.Printf("connection %d stack trace:\n%s", id, debug.Stack())
		}
	}()
	defer func() {
		if err := connection.Close(); err != nil {
			log.Printf("connection %d close error: %v", id, err)
		}
	}()
	clients.Set(id, executor.ClientInfo{ID: id})
	defer clients.Remove(id)
	defer log.Printf("connection %d closed", id)
	log.Printf("connection %d accepted from %s", id, connection.RemoteAddr())
	reader, writer := bufio.NewReader(connection), bufio.NewWriter(connection)
	requestCount := 0
	for {
		fields, err := resp.ReadRequest(reader)
		if err != nil {
			if errors.Is(err, io.EOF) {
				log.Printf("connection %d reached EOF after %d request(s)", id, requestCount)
			} else {
				log.Printf("connection %d read request error: %v", id, err)
				_ = writeAndFlush(id, writer, redis.GenericError("invalid request"), "protocol error reply")
			}
			return
		}
		command, err := redis.Interpret(fields)
		nextRequestCount := requestCount + 1
		if err != nil {
			log.Printf("connection %d request %d rejected: %v", id, nextRequestCount, err)
			_ = writeAndFlush(id, writer, errorReplyForInterpret(err), "parse error reply")
			continue
		}
		requestCount = nextRequestCount
		started := time.Now()
		log.Printf("connection %d request %d started command=%s args=%d", id, requestCount, command.Verb, len(command.Args))
		reply := executor.Execute(&command, c, id, clients)
		if err := writeAndFlush(id, writer, reply, fmt.Sprintf("reply for command=%s", command.Verb)); err != nil {
			return
		}
		log.Printf("connection %d request %d completed command=%s duration=%s", id, requestCount, command.Verb, time.Since(started))
	}
}

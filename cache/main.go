package main

import (
	"bufio"
	"context"
	"log"
	"net"
	"os"
	"strings"
	"syscall"
	"time"

	"os/signal"
	"paralyabot-cache/cache"
	"paralyabot-cache/resp"
	"paralyabot-cache/server"
)

const (
	port    = ":6379"
	timeout = 500 * time.Millisecond
)

func main() {
	if len(os.Args) > 1 && os.Args[1] == "-healthcheck" {
		log.Println("Running health check")
		os.Exit(runHealthCheck())
	}
	log.Println("Starting ParalyaBot cache")
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	c := cache.NewCache()
	defer c.Stop()
	if err := server.Serve(ctx, c, port); err != nil {
		log.Println(err)
		return
	}
}

func runHealthCheck() int {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	var d net.Dialer
	conn, err := d.DialContext(ctx, "tcp", "localhost"+port)
	if err != nil {
		log.Println("healthcheck dial failed:", err)
		return 1
	}
	defer func() {
		if cerr := conn.Close(); cerr != nil {
			log.Println("healthcheck close failed:", cerr)
		}
	}()
	deadline := time.Now().Add(timeout)
	if err := conn.SetDeadline(deadline); err != nil {
		log.Println("healthcheck setdeadline failed:", err)
		return 1
	}

	if _, err := conn.Write(resp.ArrayReply(resp.BulkString([]byte("PING")))); err != nil {
		log.Println("healthcheck write failed:", err)
		return 1
	}

	reader := bufio.NewReader(conn)
	line, err := reader.ReadString('\n')
	if err != nil {
		log.Println("healthcheck read failed:", err)
		return 1
	}

	line = strings.TrimRight(line, "\r\n")
	if line != "+PONG" {
		log.Println("healthcheck unexpected response:", line)
		return 1
	}
	return 0
}

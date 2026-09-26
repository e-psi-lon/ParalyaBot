package server

import (
	"bufio"
	"bytes"
	"errors"
	"testing"

	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
)

func TestErrorReplyForInterpret(t *testing.T) {
	testCases := []struct {
		name string
		err  error
		want string
	}{
		{
			name: "unknown command",
			err:  &redis.UnknownVerbError{Verb: "WAT"},
			want: "-ERR unknown command \"WAT\"\r\n",
		},
		{
			name: "arity error",
			err:  &redis.ArityError[redis.CommandType]{Verb: redis.HGET, Got: 1},
			want: "-ERR wrong number of arguments for \"HGET\" command\r\n",
		},
		{
			name: "fallback protocol error",
			err:  errors.New("not a redis error"),
			want: "-ERR protocol error\r\n",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			got := errorReplyForInterpret(testCase.err)
			if string(got) != testCase.want {
				t.Fatalf("errorReplyForInterpret(%v) = %q, want %q", testCase.err, got, testCase.want)
			}
		})
	}
}

func TestWriteAndFlush(t *testing.T) {
	var buf bytes.Buffer
	writer := bufio.NewWriter(&buf)

	if err := writeAndFlush(42, writer, resp.SimpleString("OK"), "ping"); err != nil {
		t.Fatalf("writeAndFlush() unexpected error: %v", err)
	}

	if got, want := buf.String(), "+OK\r\n"; got != want {
		t.Fatalf("writeAndFlush() wrote %q, want %q", got, want)
	}
}

type errReplyWriter struct{}

var errReplyWriteFailed = errors.New("write failed")

func (errReplyWriter) Write([]byte) (int, error) {
	return 0, errReplyWriteFailed
}

func TestWriteAndFlushPropagatesWriteError(t *testing.T) {
	writer := bufio.NewWriterSize(errReplyWriter{}, 1)

	err := writeAndFlush(42, writer, resp.SimpleString("OK"), "ping")
	if !errors.Is(err, errReplyWriteFailed) {
		t.Fatalf("writeAndFlush() error = %v, want %v", err, errReplyWriteFailed)
	}
}

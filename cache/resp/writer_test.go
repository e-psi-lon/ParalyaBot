//nolint:goconst
package resp

import (
	"bufio"
	"bytes"
	"errors"
	"testing"
)

func TestSimpleString(t *testing.T) {
	got := SimpleString("OK")
	want := "+OK\r\n"
	if string(got) != want {
		t.Fatalf("SimpleString() = %q, want %q", got, want)
	}
}

func TestError(t *testing.T) {
	got := Error("ERR something bad happened")
	want := "-ERR something bad happened\r\n"
	if string(got) != want {
		t.Fatalf("Error() = %q, want %q", got, want)
	}
}

func TestInteger(t *testing.T) {
	testCases := []struct {
		name string
		in   int
		want string
	}{
		{name: "positive", in: 42, want: ":42\r\n"},
		{name: "zero", in: 0, want: ":0\r\n"},
		{name: "negative", in: -7, want: ":-7\r\n"},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			got := Integer(testCase.in)
			if string(got) != testCase.want {
				t.Fatalf("Integer(%d) = %q, want %q", testCase.in, got, testCase.want)
			}
		})
	}
}

func TestInteger64(t *testing.T) {
	got := Integer64(9223372036854775807)
	want := ":9223372036854775807\r\n"
	if string(got) != want {
		t.Fatalf("Integer64() = %q, want %q", got, want)
	}
}

func TestBulkString(t *testing.T) {
	testCases := []struct {
		name string
		in   []byte
		want string
	}{
		{name: "nil is null bulk string", in: nil, want: "$-1\r\n"},
		{name: "empty non-nil bulk string", in: []byte{}, want: "$0\r\n\r\n"},
		{name: "normal bulk string", in: []byte("HGET"), want: "$4\r\nHGET\r\n"},
		{
			name: "bulk string containing CRLF is binary-safe",
			in:   []byte("a\r\nb"),
			want: "$4\r\na\r\nb\r\n",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			got := BulkString(testCase.in)
			if string(got) != testCase.want {
				t.Fatalf("BulkString(%q) = %q, want %q", testCase.in, got, testCase.want)
			}
		})
	}
}

func TestArrayReply(t *testing.T) {
	testCases := []struct {
		name string
		in   []Reply
		want string
	}{
		{name: "no elements", in: nil, want: "*0\r\n"},
		{
			name: "single bulk string element",
			in:   []Reply{BulkString([]byte("PING"))},
			want: "*1\r\n$4\r\nPING\r\n",
		},
		{
			name: "command-shaped array",
			in:   []Reply{BulkString([]byte("HGET")), BulkString([]byte("bucket"))},
			want: "*2\r\n$4\r\nHGET\r\n$6\r\nbucket\r\n",
		},
		{
			name: "array containing a null bulk string element",
			in:   []Reply{BulkString(nil)},
			want: "*1\r\n$-1\r\n",
		},
		{
			name: "mixed element types",
			in:   []Reply{Integer(1), SimpleString("OK"), Error("bad")},
			want: "*3\r\n:1\r\n+OK\r\n-bad\r\n",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			got := ArrayReply(testCase.in...)
			if string(got) != testCase.want {
				t.Fatalf("ArrayReply() = %q, want %q", got, testCase.want)
			}
		})
	}
}

func TestNilArrayReply(t *testing.T) {
	got := NilArrayReply()
	want := "*-1\r\n"
	if string(got) != want {
		t.Fatalf("NilArrayReply() = %q, want %q", got, want)
	}
}

func TestWriteReply(t *testing.T) {
	var buf bytes.Buffer
	w := bufio.NewWriter(&buf)

	reply := ArrayReply(BulkString([]byte("PONG")))
	if err := WriteReply(w, reply); err != nil {
		t.Fatalf("WriteReply() unexpected error: %v", err)
	}
	if err := w.Flush(); err != nil {
		t.Fatalf("Flush() unexpected error: %v", err)
	}

	want := "*1\r\n$4\r\nPONG\r\n"
	if buf.String() != want {
		t.Fatalf("WriteReply() wrote %q, want %q", buf.String(), want)
	}
}

type errWriter struct{}

var errWriteFailed = errors.New("write failed")

func (errWriter) Write([]byte) (int, error) {
	return 0, errWriteFailed
}

func TestWriteReplyPropagatesWriteError(t *testing.T) {
	// Buffer size of 1 forces bufio.Writer to flush to the underlying
	// writer immediately, so the error surfaces from WriteReply itself
	// rather than requiring an explicit Flush().
	w := bufio.NewWriterSize(errWriter{}, 1)

	err := WriteReply(w, SimpleString("OK"))
	if !errors.Is(err, errWriteFailed) {
		t.Fatalf("WriteReply() error = %v, want %v", err, errWriteFailed)
	}
}

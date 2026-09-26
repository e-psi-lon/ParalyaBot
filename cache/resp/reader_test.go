//nolint:goconst
package resp

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"reflect"
	"strings"
	"testing"
)

func TestReadRequest(t *testing.T) {
	testCases := []struct {
		name        string
		input       string
		want        [][]byte
		wantErr     error
		wantMessage string
	}{
		{
			name:  "reads bulk string array",
			input: "*2\r\n$4\r\nHGET\r\n$6\r\nbucket\r\n",
			want:  [][]byte{[]byte("HGET"), []byte("bucket")},
		},
		{
			name:  "reads empty array",
			input: "*0\r\n",
			want:  [][]byte{},
		},
		{
			name:  "reads null bulk string",
			input: "*1\r\n$-1\r\n",
			want:  [][]byte{nil},
		},
		{
			name:    "rejects invalid array length",
			input:   "*-1\r\n",
			wantErr: ErrInvalidArrayLength,
		},
		{
			name:    "rejects wrong array prefix",
			input:   "$1\r\n",
			wantErr: ErrBadPrefix,
		},
		{
			name:        "rejects missing line terminator",
			input:       "*1\n",
			wantMessage: "line missing CRLF terminator",
		},
		{
			name:        "rejects invalid bulk length",
			input:       "*1\r\n$-2\r\n",
			wantMessage: "invalid bulk string length (got -2)",
		},
		{
			name:        "rejects oversized array",
			input:       fmt.Sprintf("*%d\r\n", maxArrayElements+1),
			wantMessage: "too many array elements",
		},
		{
			name:        "rejects oversized bulk string",
			input:       fmt.Sprintf("*1\r\n$%d\r\n", maxBulkStringSize+1),
			wantMessage: "bulk string too large",
		},
		{
			name:        "rejects truncated bulk string",
			input:       "*1\r\n$3\r\nab",
			wantMessage: "unexpected EOF",
		},
		{
			name:        "rejects invalid bulk terminator",
			input:       "*1\r\n$3\r\nabcX\n",
			wantMessage: "expected '\\r'",
		},
		{
			name:        "rejects bulk terminator missing final LF",
			input:       "*1\r\n$3\r\nabc\rX",
			wantMessage: "expected '\\n'",
		},
		{
			name:        "rejects bulk terminator truncated after CR",
			input:       "*1\r\n$3\r\nabc\r",
			wantMessage: "EOF",
		},
		{
			name:        "rejects oversized line",
			input:       strings.Repeat("a", maxLineBytes+1) + "\n",
			wantMessage: "line too long",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			request, err := ReadRequest(bufio.NewReader(strings.NewReader(testCase.input)))
			if testCase.wantErr != nil {
				if !errors.Is(err, testCase.wantErr) {
					t.Fatalf("ReadRequest() error = %v, want %v", err, testCase.wantErr)
				}
				return
			}
			if testCase.wantMessage != "" {
				if err == nil || !strings.Contains(err.Error(), testCase.wantMessage) {
					t.Fatalf("ReadRequest() error = %v, want message containing %q", err, testCase.wantMessage)
				}
				return
			}
			if err != nil {
				t.Fatalf("ReadRequest() unexpected error: %v", err)
			}
			if !reflect.DeepEqual(request, testCase.want) {
				t.Fatalf("ReadRequest() = %q, want %q", request, testCase.want)
			}
		})
	}
}

func TestReadRequestDoesNotConsumeFollowingRequest(t *testing.T) {
	input := "*1\r\n$4\r\nPING\r\n*1\r\n$4\r\nPONG\r\n"
	reader := bufio.NewReader(bytes.NewBufferString(input))

	first, err := ReadRequest(reader)
	if err != nil {
		t.Fatalf("first ReadRequest() unexpected error: %v", err)
	}
	second, err := ReadRequest(reader)
	if err != nil {
		t.Fatalf("second ReadRequest() unexpected error: %v", err)
	}
	if !reflect.DeepEqual(first, [][]byte{[]byte("PING")}) {
		t.Fatalf("first request = %q, want %q", first, [][]byte{[]byte("PING")})
	}
	if !reflect.DeepEqual(second, [][]byte{[]byte("PONG")}) {
		t.Fatalf("second request = %q, want %q", second, [][]byte{[]byte("PONG")})
	}
}

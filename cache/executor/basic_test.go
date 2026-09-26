//nolint:goconst
package executor

import (
	"strings"
	"testing"

	"paralyabot-cache/cache"
)

func TestHandlePing(t *testing.T) {
	testCases := []struct {
		name string
		args [][]byte
		want string
	}{
		{
			name: "no arguments returns simple PONG",
			args: nil,
			want: "+PONG\r\n",
		},
		{
			name: "single argument is echoed back as bulk string",
			args: [][]byte{[]byte("hello")},
			want: "$5\r\nhello\r\n",
		},
		{
			name: "empty single argument is echoed back",
			args: [][]byte{[]byte("")},
			want: "$0\r\n\r\n",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			got := handlePing(testCase.args)
			if string(got) != testCase.want {
				t.Fatalf("handlePing(%q) = %q, want %q", testCase.args, got, testCase.want)
			}
		})
	}
}

func TestHandlePingWrongArity(t *testing.T) {
	got := handlePing([][]byte{[]byte("a"), []byte("b")})

	s := string(got)
	if !strings.HasPrefix(s, "-ERR ") || !strings.Contains(s, "wrong number of arguments") {
		t.Fatalf("handlePing(2 args) = %q, want an error containing %q", s, "wrong number of arguments")
	}
}

func TestHandleDel(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()
	c.Set("b1", "f", []byte("v"))
	c.Set("b2", "f", []byte("v"))

	// b1 and b2 exist, "missing" does not: expect 2 buckets deleted.
	got := handleDel([][]byte{[]byte("b1"), []byte("missing"), []byte("b2")}, c)

	if string(got) != ":2\r\n" {
		t.Fatalf("handleDel = %q, want %q", got, ":2\r\n")
	}
	if _, ok := c.Get("b1", "f"); ok {
		t.Fatal("b1 still present after handleDel")
	}
	if _, ok := c.Get("b2", "f"); ok {
		t.Fatal("b2 still present after handleDel")
	}
}

func TestHandleDelNoneExist(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()

	got := handleDel([][]byte{[]byte("nonexistent")}, c)

	if string(got) != ":0\r\n" {
		t.Fatalf("handleDel on nonexistent bucket = %q, want %q", got, ":0\r\n")
	}
}

func TestHandleHelloNoArgs(t *testing.T) {
	got := handleHello(nil, 42)

	s := string(got)
	if !strings.HasPrefix(s, "*") {
		t.Fatalf("handleHello() = %q, want an array reply", s)
	}
	if !strings.Contains(s, "ParalyaBot Cache") {
		t.Fatalf("handleHello() = %q, want it to contain the server name", s)
	}
}

func TestHandleHelloValidVersion(t *testing.T) {
	got := handleHello([][]byte{[]byte("2")}, 42)

	s := string(got)
	if !strings.HasPrefix(s, "*") {
		t.Fatalf("handleHello(2) = %q, want an array reply", s)
	}
}

func TestHandleHelloNonIntegerVersion(t *testing.T) {
	got := handleHello([][]byte{[]byte("notanumber")}, 42)

	s := string(got)
	if !strings.HasPrefix(s, "-ERR ") {
		t.Fatalf("handleHello(notanumber) = %q, want a generic ERR reply", s)
	}
}

func TestHandleHelloUnsupportedVersion(t *testing.T) {
	got := handleHello([][]byte{[]byte("3")}, 42)

	s := string(got)
	if !strings.HasPrefix(s, "-NOPROTO ") {
		t.Fatalf("handleHello(3) = %q, want a NOPROTO reply, not ERR", s)
	}
}

func TestHandleHelloTooManyArgs(t *testing.T) {
	got := handleHello([][]byte{[]byte("2"), []byte("extra")}, 42)

	s := string(got)
	if !strings.HasPrefix(s, "-ERR ") {
		t.Fatalf("handleHello(2, extra) = %q, want a NOPROTO reply", s)
	}
}

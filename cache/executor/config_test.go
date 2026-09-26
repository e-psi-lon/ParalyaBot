//nolint:goconst
package executor

import (
	"strings"
	"testing"

	"paralyabot-cache/cache"
)

func TestHandleConfig(t *testing.T) {
	arg := func(s ...string) [][]byte {
		out := make([][]byte, len(s))
		for i, v := range s {
			out[i] = []byte(v)
		}
		return out
	}

	tests := []struct {
		name string
		args [][]byte
		pre  func(c *cache.Cache)
		want string // exact reply, or "-ERR" for an ERR prefix
	}{
		{"get with no parameter", arg("GET"), nil, "-ERR"},
		{"get unknown param", arg("GET", "nonsense"), nil, "*0\r\n"},
		{"get maxmemory for unset bucket", arg("GET", "b:maxmemory"), nil, "*0\r\n"},
		{"get maxmemory for configured bucket", arg("GET", "b:maxmemory"),
			func(c *cache.Cache) { c.SetMaxBucketSize("b", 42) },
			"*2\r\n$11\r\nb:maxmemory\r\n$2\r\n42\r\n"},
		{"set valid", arg("SET", "b:maxmemory", "100"), nil, "+OK\r\n"},
		{"set zero disables the cap", arg("SET", "b:maxmemory", "0"), nil, "+OK\r\n"},
		{"set non-integer", arg("SET", "b:maxmemory", "abc"), nil, "-ERR"},
		{"set negative", arg("SET", "b:maxmemory", "-1"), nil, "-ERR"},
		{"set unknown param", arg("SET", "other", "1"), nil, "-ERR"},
		{"set missing value", arg("SET", "b:maxmemory"), nil, "-ERR"},
		{"unknown subcommand", arg("BOGUS"), nil, "-ERR"},
		{"no subcommand", nil, nil, "-ERR"},
		{"set missing value", arg("SET", "b:maxmemory"), nil, "-ERR wrong number of arguments for CONFIG|SET command\r\n"},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			c := cache.NewCache()
			defer c.Stop()
			if tc.pre != nil {
				tc.pre(c)
			}
			got := string(handleConfig(tc.args, c))
			if tc.want == "-ERR" {
				if !strings.HasPrefix(got, "-ERR") {
					t.Fatalf("got %q, want an ERR reply", got)
				}
				return
			}
			if got != tc.want {
				t.Fatalf("got %q, want %q", got, tc.want)
			}
		})
	}
}

func TestConfigSetTakesEffect(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()

	if got := string(handleConfig([][]byte{[]byte("SET"), []byte("b:maxmemory"), []byte("100")}, c)); got != "+OK\r\n" {
		t.Fatalf("CONFIG SET = %q", got)
	}
	if size, ok := c.GetMaxBucketSize("b"); !ok || size != 100 {
		t.Fatalf("GetMaxBucketSize() = %d, %v; want 100, true", size, ok)
	}
}

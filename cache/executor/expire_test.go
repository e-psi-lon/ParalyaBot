//nolint:goconst
package executor

import (
	"strings"
	"testing"

	"paralyabot-cache/cache"
)

func TestHandleHexpire(t *testing.T) {
	arg := func(s ...string) [][]byte {
		out := make([][]byte, len(s))
		for i, v := range s {
			out[i] = []byte(v)
		}
		return out
	}

	tests := []struct {
		name  string
		setup func(c *cache.Cache)
		args  [][]byte
		want  string
	}{
		{
			name:  "existing field gets TTL",
			setup: func(c *cache.Cache) { c.Set("b", "f", []byte("v")) },
			args:  arg("b", "100", "FIELDS", "1", "f"),
			want:  "*1\r\n:1\r\n",
		},
		{
			name:  "missing field",
			setup: func(c *cache.Cache) {},
			args:  arg("b", "100", "FIELDS", "1", "f"),
			want:  "*1\r\n:-2\r\n",
		},
		{
			name:  "mixed fields keep positions",
			setup: func(c *cache.Cache) { c.Set("b", "f1", []byte("v")) },
			args:  arg("b", "100", "FIELDS", "2", "f1", "nope"),
			want:  "*2\r\n:1\r\n:-2\r\n",
		},
		{
			name:  "zero TTL deletes field",
			setup: func(c *cache.Cache) { c.Set("b", "f", []byte("v")) },
			args:  arg("b", "0", "FIELDS", "1", "f"),
			want:  "*1\r\n:2\r\n",
		},
		{
			name: "NX fails when TTL already set",
			setup: func(c *cache.Cache) {
				c.Set("b", "f", []byte("v"))
				c.Expire("b", "f", 100, cache.NONE)
			},
			args: arg("b", "200", "NX", "FIELDS", "1", "f"),
			want: "*1\r\n:0\r\n",
		},
		{
			name:  "numfields larger than fields given",
			setup: func(c *cache.Cache) {},
			args:  arg("b", "100", "FIELDS", "3", "f"),
			want:  "-ERR",
		},
		{
			name:  "non-integer TTL",
			setup: func(c *cache.Cache) {},
			args:  arg("b", "abc", "FIELDS", "1", "f"),
			want:  "-ERR",
		},
		{
			name:  "missing FIELDS keyword",
			setup: func(c *cache.Cache) {},
			args:  arg("b", "100", "1", "f"),
			want:  "-ERR",
		},
		{
			name:  "seconds overflows time.Duration",
			setup: func(c *cache.Cache) {},
			args:  arg("b", "99999999999999999", "FIELDS", "1", "f"),
			want:  "-ERR",
		},
		{
			name:  "FIELDS with nothing after it",
			setup: func(c *cache.Cache) {},
			args:  arg("b", "100", "FIELDS"),
			want:  "-ERR",
		},
		{
			name:  "non-integer field count",
			setup: func(c *cache.Cache) {},
			args:  arg("b", "100", "FIELDS", "abc", "f"),
			want:  "-ERR",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			c := cache.NewCache()
			defer c.Stop()
			tc.setup(c)

			got := string(handleHexpire(tc.args, c))
			if strings.HasPrefix(tc.want, "-ERR") {
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

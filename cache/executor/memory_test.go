//nolint:goconst
package executor

import (
	"strings"
	"testing"

	"paralyabot-cache/cache"
)

func TestHandleMemory(t *testing.T) {
	arg := func(values ...string) [][]byte {
		args := make([][]byte, len(values))
		for i, value := range values {
			args[i] = []byte(value)
		}
		return args
	}

	tests := []struct {
		name string
		args [][]byte
		pre  func(c *cache.Cache)
		want string
	}{
		{"usage missing bucket", arg("USAGE", "missing"), nil, "$-1\r\n"},
		{"usage existing bucket", arg("USAGE", "bucket"), func(c *cache.Cache) {
			c.Set("bucket", "field", []byte("value"))
		}, ":5\r\n"},
		{"unknown subcommand", arg("BOGUS"), nil, "-ERR"},
		{"no subcommand", nil, nil, "-ERR"},
		{"usage missing argument", arg("USAGE"), nil, "-ERR"},
		{"stats extra argument", arg("STATS", "extra"), nil, "-ERR"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			c := cache.NewCache()
			defer c.Stop()
			if test.pre != nil {
				test.pre(c)
			}
			got := string(handleMemory(test.args, c))
			if test.want == "-ERR" {
				if !strings.HasPrefix(got, "-ERR") {
					t.Fatalf("handleMemory(%q) = %q, want an ERR reply", test.args, got)
				}
				return
			}
			if got != test.want {
				t.Fatalf("handleMemory(%q) = %q, want %q", test.args, got, test.want)
			}
		})
	}
}

func TestHandleMemoryStats(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()
	c.Set("bucket", "one", []byte("123"))
	c.Set("bucket", "two", []byte("45"))
	c.Set("other", "field", []byte("6"))

	reply := string(handleMemory([][]byte{[]byte("STATS")}, c))
	if !strings.HasPrefix(reply, "*54\r\n") {
		t.Fatalf("MEMORY STATS reply = %q, want an array of 54 elements", reply)
	}
	for _, metric := range []string{
		"$10\r\nkeys.count\r\n:2\r\n",
		"$18\r\nkeys.bytes-per-key\r\n:3\r\n",
		"$13\r\ndataset.bytes\r\n:6\r\n",
		"$18\r\ndataset.percentage\r\n:100\r\n",
		"$15\r\npeak.percentage\r\n:100\r\n",
		"$29\r\nallocator-fragmentation.ratio\r\n:1\r\n",
	} {
		if !strings.Contains(reply, metric) {
			t.Errorf("MEMORY STATS reply missing metric %q", metric)
		}
	}
}

func TestAverage(t *testing.T) {
	tests := []struct {
		name    string
		payload int64
		keys    int64
		want    int64
	}{
		{"zero keys", 12, 0, 0},
		{"truncates", 5, 2, 2},
		{"exact", 6, 2, 3},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := average(test.payload, test.keys); got != test.want {
				t.Fatalf("average(%d, %d) = %d, want %d", test.payload, test.keys, got, test.want)
			}
		})
	}
}

func TestPercentage(t *testing.T) {
	tests := []struct {
		name  string
		value int64
		total int64
		want  int64
	}{
		{"zero total", 12, 0, 0},
		{"truncates", 1, 3, 33},
		{"full value", 12, 12, 100},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := percentage(test.value, test.total); got != test.want {
				t.Fatalf("percentage(%d, %d) = %d, want %d", test.value, test.total, got, test.want)
			}
		})
	}
}

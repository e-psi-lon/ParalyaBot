//nolint:goconst
package executor

import (
	"bufio"
	"bytes"
	"reflect"
	"testing"

	"paralyabot-cache/cache"
	"paralyabot-cache/resp"
)

func TestHandleHSet(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()

	// New field: existing Redis semantics report 1 (field created).
	got := handleHSet([][]byte{[]byte("bucket"), []byte("field"), []byte("v1")}, c)
	if string(got) != ":1\r\n" {
		t.Fatalf("HSET on new field = %q, want %q", got, ":1\r\n")
	}

	// Overwriting the same field: reports 0 (no new field created).
	got = handleHSet([][]byte{[]byte("bucket"), []byte("field"), []byte("v2")}, c)
	if string(got) != ":0\r\n" {
		t.Fatalf("HSET overwrite = %q, want %q", got, ":0\r\n")
	}

	// Confirm the overwrite actually took effect.
	value, ok := c.Get("bucket", "field")
	if !ok || string(value) != "v2" {
		t.Fatalf("cache value after overwrite = (%q, %v), want (%q, true)", value, ok, "v2")
	}
}

func TestHandleHGet(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()
	c.Set("bucket", "field", []byte("value"))

	got := handleHGet([][]byte{[]byte("bucket"), []byte("field")}, c)
	if string(got) != "$5\r\nvalue\r\n" {
		t.Fatalf("HGET existing field = %q, want %q", got, "$5\r\nvalue\r\n")
	}
}

func TestHandleHGetMissingField(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()

	got := handleHGet([][]byte{[]byte("bucket"), []byte("nope")}, c)
	if string(got) != "$-1\r\n" {
		t.Fatalf("HGET missing field = %q, want %q (null bulk string)", got, "$-1\r\n")
	}
}

func TestHandleHMGet(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()
	c.Set("bucket", "f1", []byte("v1"))
	c.Set("bucket", "f2", []byte("v2"))

	got := handleHMGet([][]byte{[]byte("bucket"), []byte("f1"), []byte("missing"), []byte("f2")}, c)
	want := "*3\r\n$2\r\nv1\r\n$-1\r\n$2\r\nv2\r\n"
	if string(got) != want {
		t.Fatalf("HMGET = %q, want %q", got, want)
	}
}

func TestHandleHVals(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()
	c.Set("bucket", "f1", []byte("a"))
	c.Set("bucket", "f2", []byte("b"))

	got := handleHVals([][]byte{[]byte("bucket")}, c)

	// Vals() iterates a Go map, so order isn't guaranteed. Parse the reply
	// back with resp.ReadRequest (it reads any array-of-bulk-strings, not
	// just commands) and compare as a set instead of asserting byte order.
	gotValues := readBulkStringArray(t, got)
	wantValues := []string{"a", "b"}
	if !sameSet(gotValues, wantValues) {
		t.Fatalf("HVALS values = %v, want set %v", gotValues, wantValues)
	}
}

func TestHandleHValsEmptyBucket(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()

	got := handleHVals([][]byte{[]byte("nonexistent")}, c)
	if string(got) != "*0\r\n" {
		t.Fatalf("HVALS on nonexistent bucket = %q, want %q", got, "*0\r\n")
	}
}

func TestHandleHLen(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()
	c.Set("bucket", "f1", []byte("a"))
	c.Set("bucket", "f2", []byte("b"))

	got := handleHLen([][]byte{[]byte("bucket")}, c)
	if string(got) != ":2\r\n" {
		t.Fatalf("HLEN = %q, want %q", got, ":2\r\n")
	}
}

func TestHandleHLenNonexistentBucket(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()

	got := handleHLen([][]byte{[]byte("nonexistent")}, c)
	if string(got) != ":0\r\n" {
		t.Fatalf("HLEN on nonexistent bucket = %q, want %q", got, ":0\r\n")
	}
}

func TestHandleHDel(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()
	c.Set("bucket", "f1", []byte("a"))
	c.Set("bucket", "f2", []byte("b"))

	got := handleHDel([][]byte{[]byte("bucket"), []byte("f1"), []byte("missing"), []byte("f2")}, c)
	if string(got) != ":2\r\n" {
		t.Fatalf("HDEL = %q, want %q (2 real deletions, 1 miss)", got, ":2\r\n")
	}

	if _, ok := c.Get("bucket", "f1"); ok {
		t.Fatal("f1 still present after HDEL")
	}
}

func TestHandleHDelNoFieldsDeleted(t *testing.T) {
	c := cache.NewCache()
	defer c.Stop()

	got := handleHDel([][]byte{[]byte("bucket"), []byte("missing")}, c)
	if string(got) != ":0\r\n" {
		t.Fatalf("HDEL on missing field = %q, want %q", got, ":0\r\n")
	}
}

// readBulkStringArray parses a *N\r\n...-shaped reply (array of bulk
// strings) back into plain string values using the package's own reader,
// for assertions where element order isn't guaranteed (e.g. iterating a
// Go map). Fails the test if the reply doesn't parse.
func readBulkStringArray(t *testing.T, reply []byte) []string {
	t.Helper()
	fields, err := resp.ReadRequest(bufio.NewReader(bytes.NewReader(reply)))
	if err != nil {
		t.Fatalf("readBulkStringArray: failed to parse %q: %v", reply, err)
	}
	values := make([]string, len(fields))
	for i, f := range fields {
		values[i] = string(f)
	}
	return values
}

func sameSet(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	am := map[string]int{}
	for _, v := range a {
		am[v]++
	}
	bm := map[string]int{}
	for _, v := range b {
		bm[v]++
	}
	return reflect.DeepEqual(am, bm)
}

package cache

import (
	"bytes"
	"errors"
	"sync"
	"testing"
	"testing/synctest"
	"time"
)

func newTestCache(t *testing.T) *Cache {
	t.Helper()
	c := NewCache()
	t.Cleanup(c.Stop)
	return c
}

func TestCacheSetGetAndValueOwnership(t *testing.T) {
	c := newTestCache(t)

	value := []byte("value")
	if c.Set("bucket", "field", value) {
		t.Fatal("first Set should report that the field did not exist")
	}
	value[0] = 'X'
	got, ok := c.Get("bucket", "field")
	if !ok || !bytes.Equal(got, []byte("value")) {
		t.Fatalf("Get() = %q, %v; want value, true", got, ok)
	}
	got[0] = 'Y'
	got, ok = c.Get("bucket", "field")
	if !ok || !bytes.Equal(got, []byte("value")) {
		t.Fatalf("Get() did not protect stored bytes: %q, %v", got, ok)
	}
	if !c.Set("bucket", "field", []byte("new")) {
		t.Fatal("replacement Set should report that the field existed")
	}
	if c.Len("bucket") != 1 {
		t.Fatalf("Len() = %d, want 1", c.Len("bucket"))
	}
}

func TestCacheMissingAndDeleteOperations(t *testing.T) {
	c := newTestCache(t)

	if _, ok := c.Get("missing", "field"); ok {
		t.Fatal("Get() reported a missing field")
	}
	if c.Delete("missing", "field") {
		t.Fatal("Delete() reported a missing field")
	}
	if c.DeleteBucket("missing") {
		t.Fatal("DeleteBucket() reported a missing bucket")
	}
	if _, ok := c.MemoryUsage("missing"); ok {
		t.Fatal("MemoryUsage() reported a missing bucket")
	}

	c.Set("bucket", "field", []byte("1234"))
	if !c.Delete("bucket", "field") {
		t.Fatal("Delete() did not report the existing field")
	}
	if c.Len("bucket") != 0 || c.Delete("bucket", "field") {
		t.Fatal("deleted field remained present")
	}
	if _, ok := c.MemoryUsage("bucket"); ok {
		t.Fatal("empty bucket should be removed")
	}

	c.Set("bucket", "one", []byte("1"))
	c.Set("bucket", "two", []byte("22"))
	if !c.DeleteBucket("bucket") {
		t.Fatal("DeleteBucket() did not report the existing bucket")
	}
	payload, buckets := c.MemoryStats()
	if payload != 0 || buckets != 0 {
		t.Fatalf("MemoryStats() = %d, %d after bucket deletion; want 0, 0", payload, buckets)
	}

	c.Set("bucket", "one", []byte("1"))
	c.Set("bucket", "two", []byte("22"))
	if !c.Delete("bucket", "one") {
		t.Fatal("Delete() did not report the existing field")
	}
	if c.Delete("bucket", "one") {
		t.Fatal("second Delete() on the same field should report false")
	}
	if c.Len("bucket") != 1 {
		t.Fatalf("Len() = %d after deleting one of two fields, want 1", c.Len("bucket"))
	}
}

func TestCacheValuesAndMemoryStats(t *testing.T) {
	c := newTestCache(t)
	c.Set("one", "a", []byte("123"))
	c.Set("one", "b", []byte("45"))
	c.Set("two", "c", []byte("6"))

	values := c.Vals("one")
	if len(values) != 2 || !containsBytes(values, []byte("123")) || !containsBytes(values, []byte("45")) {
		t.Fatalf("Vals() = %q, want both stored values", values)
	}
	values[0][0] = 'X'
	if c.Len("one") != 2 {
		t.Fatalf("Len() = %d, want 2", c.Len("one"))
	}
	payload, buckets := c.MemoryStats()
	if payload != 6 || buckets != 2 {
		t.Fatalf("MemoryStats() = %d, %d; want 6, 2", payload, buckets)
	}
	usage, ok := c.MemoryUsage("one")
	if !ok || usage != 5 {
		t.Fatalf("MemoryUsage() = %d, %v; want 5, true", usage, ok)
	}
	if c.Vals("missing") != nil || c.Len("missing") != 0 {
		t.Fatal("missing bucket should have no values and length zero")
	}
}

func TestCacheExpireConditions(t *testing.T) {
	c := newTestCache(t)
	if status := c.Expire("bucket", "field", 1, NONE); status != StatusNoSuchField {
		t.Fatalf("Expire() for missing field = %d, want %d", status, StatusNoSuchField)
	}
	c.Set("bucket", "field", []byte("value"))

	if status := c.Expire("bucket", "field", 1, NX); status != StatusSet {
		t.Fatalf("NX expiry = %d, want %d", status, StatusSet)
	}
	if status := c.Expire("bucket", "field", 2, NX); status != StatusConditionNotMet {
		t.Fatalf("second NX expiry = %d, want %d", status, StatusConditionNotMet)
	}
	if status := c.Expire("bucket", "field", 1, XX); status != StatusSet {
		t.Fatalf("XX expiry = %d, want %d", status, StatusSet)
	}
	if status := c.Expire("bucket", "field", 2, GT); status != StatusSet {
		t.Fatalf("GT expiry = %d, want %d", status, StatusSet)
	}
	if status := c.Expire("bucket", "field", 1, GT); status != StatusConditionNotMet {
		t.Fatalf("GT non-increasing expiry = %d, want %d", status, StatusConditionNotMet)
	}
	if status := c.Expire("bucket", "field", 1, LT); status != StatusSet {
		t.Fatalf("LT expiry = %d, want %d", status, StatusSet)
	}
	if status := c.Expire("bucket", "field", 2, LT); status != StatusConditionNotMet {
		t.Fatalf("LT non-decreasing expiry = %d, want %d", status, StatusConditionNotMet)
	}
	if status := c.Expire("bucket", "field", 0, NONE); status != StatusDeleted {
		t.Fatalf("zero expiry = %d, want %d", status, StatusDeleted)
	}
	if _, ok := c.Get("bucket", "field"); ok {
		t.Fatal("zero expiry did not delete the field")
	}

	c.Set("bucket", "field", []byte("value"))
	if status := c.Expire("bucket", "field", 1, XX); status != StatusConditionNotMet {
		t.Fatalf("XX on persistent field = %d, want %d", status, StatusConditionNotMet)
	}
	if status := c.Expire("bucket", "field", -1, NONE); status != StatusDeleted {
		t.Fatalf("negative expiry = %d, want %d", status, StatusDeleted)
	}
	if status := c.Expire("bucket", "field", int(maxExpirationSeconds)+1, NONE); status != StatusNoSuchField {
		t.Fatalf("oversized expiry = %d, want %d", status, StatusNoSuchField)
	}
}

func TestCacheExpiredItemsAndCleanup(t *testing.T) {
	c := newTestCache(t)
	c.Set("bucket", "expired", []byte("old"))
	c.Set("bucket", "live", []byte("new"))
	c.mu.Lock()
	c.buckets["bucket"].items["expired"] = bucketItem{Value: []byte("old"), ExpiresAt: time.Now().Add(-time.Second)}
	c.buckets["bucket"].size = 6
	c.mu.Unlock()

	if _, ok := c.Get("bucket", "expired"); ok {
		t.Fatal("Get() returned an expired item")
	}
	if c.Len("bucket") != 1 || len(c.Vals("bucket")) != 1 {
		t.Fatal("expired item was included in collection results")
	}
	c.cleanup()
	if c.Len("bucket") != 1 {
		t.Fatalf("cleanup removed live item; Len() = %d", c.Len("bucket"))
	}
}

func TestCacheExpireOnAlreadyExpiredField(t *testing.T) {
	c := newTestCache(t)
	c.Set("bucket", "field", []byte("value"))
	c.mu.Lock()
	c.buckets["bucket"].items["field"] = bucketItem{
		Value:     []byte("value"),
		ExpiresAt: time.Now().Add(-time.Second),
	}
	c.mu.Unlock()

	if status := c.Expire("bucket", "field", 5, NONE); status != StatusNoSuchField {
		t.Fatalf("Expire() on expired field = %d, want %d", status, StatusNoSuchField)
	}
	if _, ok := c.Get("bucket", "field"); ok {
		t.Fatal("Expire() did not delete the already-expired field")
	}
	if c.Len("bucket") != 0 {
		t.Fatalf("Len() = %d after expiring the only field, want 0", c.Len("bucket"))
	}
}

func TestCacheExpireMissingFieldInExistingBucket(t *testing.T) {
	c := newTestCache(t)
	c.Set("bucket", "other", []byte("value"))

	if status := c.Expire("bucket", "field", 5, NONE); status != StatusNoSuchField {
		t.Fatalf("Expire() on missing field = %d, want %d", status, StatusNoSuchField)
	}
}

func TestCacheEvictsLeastRecentlyAccessedItems(t *testing.T) {
	c := newTestCache(t)
	c.SetMaxBucketSize("bucket", 3)
	c.Set("bucket", "old", []byte("aa"))
	c.Set("bucket", "new", []byte("bb"))
	c.mu.Lock()
	c.buckets["bucket"].items["old"] = bucketItem{Value: []byte("aa"), LastAccessed: time.Unix(1, 0)}
	c.buckets["bucket"].items["new"] = bucketItem{Value: []byte("bb"), LastAccessed: time.Unix(2, 0)}
	c.buckets["bucket"].size = 4
	c.mu.Unlock()
	c.cleanup()

	if _, ok := c.Get("bucket", "old"); ok {
		t.Fatal("oldest item was not evicted")
	}
	if _, ok := c.Get("bucket", "new"); !ok {
		t.Fatal("newest item was evicted")
	}
	c.SetMaxBucketSize("unlimited", 0)
	c.Set("unlimited", "field", []byte("123"))
	c.cleanup()
	if c.Len("unlimited") != 1 {
		t.Fatal("non-positive max size should not evict items")
	}
}

func TestConditionAndMaxBucketSize(t *testing.T) {
	for _, value := range []string{"nx", "NX", "xx", "gt", "lt"} {
		if _, err := Condition(value); err != nil {
			t.Errorf("Condition(%q) returned error: %v", value, err)
		}
	}
	if condition, err := Condition("invalid"); condition != NONE || !errors.Is(err, ErrInvalidCondition) {
		t.Fatalf("invalid Condition() = %d, %v; want NONE, ErrInvalidCondition", condition, err)
	}

	c := newTestCache(t)
	c.SetMaxBucketSize("bucket", 99)
	if size, ok := c.GetMaxBucketSize("bucket"); !ok || size != 99 {
		t.Fatalf("GetMaxBucketSize() = %d, %v; want 99, true", size, ok)
	}
	if _, ok := c.GetMaxBucketSize("missing"); ok {
		t.Fatal("missing max bucket size should not be reported")
	}
}

func TestCacheConcurrentAccess(t *testing.T) {
	c := newTestCache(t)
	var group sync.WaitGroup
	for worker := range 8 {
		group.Add(1)
		go func(worker int) {
			defer group.Done()
			for range 100 {
				field := string(rune('a' + worker))
				c.Set("bucket", field, []byte("value"))
				c.Get("bucket", field)
				c.Len("bucket")
				c.Vals("bucket")
				c.Expire("bucket", field, 1, NONE)
			}
		}(worker)
	}
	group.Wait()
}

func TestCacheAfterStop(t *testing.T) {
	c := NewCache()
	c.Set("bucket", "field", []byte("value"))
	c.Stop()
	c.Stop()

	if c.Set("bucket", "field", []byte("new")) || c.Delete("bucket", "field") || c.DeleteBucket("bucket") {
		t.Fatal("mutating operations succeeded after Stop")
	}
	if _, ok := c.Get("bucket", "field"); ok {
		t.Fatal("Get() succeeded after Stop")
	}
	if c.Vals("bucket") != nil || c.Len("bucket") != 0 {
		t.Fatal("collection operations returned data after Stop")
	}
	if payload, buckets := c.MemoryStats(); payload != 0 || buckets != 0 {
		t.Fatalf("MemoryStats() after Stop = %d, %d; want 0, 0", payload, buckets)
	}
	if _, ok := c.MemoryUsage("bucket"); ok {
		t.Fatal("MemoryUsage() succeeded after Stop")
	}
	if c.Expire("bucket", "field", 1, NONE) != StatusNoSuchField {
		t.Fatal("Expire() succeeded after Stop")
	}
	if _, ok := c.GetMaxBucketSize("bucket"); ok {
		t.Fatal("GetMaxBucketSize() succeeded after Stop")
	}
	c.SetMaxBucketSize("bucket", 1)
}

func containsBytes(values [][]byte, want []byte) bool {
	for _, value := range values {
		if bytes.Equal(value, want) {
			return true
		}
	}
	return false
}

func TestCacheStopTerminatesGoroutines(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		c := NewCache() // must be constructed inside the bubble

		c.Set("bucket", "field", []byte("value"))
		c.Get("bucket", "field") // exercise the async touch path
		c.Expire("bucket", "field", 1, NONE)

		// let the sweeper tick at least once on the fake clock
		time.Sleep(10 * time.Second)
		synctest.Wait()

		c.Stop()
		c.Stop() // idempotent, as your existing test asserts
	})
}

func TestSweeperReclaimsExpiredItems(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		c := NewCache()
		defer c.Stop()

		c.Set("bucket", "short", []byte("aaaa"))
		c.Set("bucket", "long", []byte("bb"))
		c.Set("solo", "only", []byte("c"))

		if s := c.Expire("bucket", "short", 5, NONE); s != StatusSet {
			t.Fatalf("Expire short = %d", s)
		}
		if s := c.Expire("bucket", "long", 3600, NONE); s != StatusSet {
			t.Fatalf("Expire long = %d", s)
		}
		if s := c.Expire("solo", "only", 5, NONE); s != StatusSet {
			t.Fatalf("Expire solo = %d", s)
		}

		// Past the deadline but before any sweep is guaranteed to have run:
		// reads must already treat it as gone (validity lives on the item).
		time.Sleep(6 * time.Second)
		if _, ok := c.Get("bucket", "short"); ok {
			t.Fatal("expired item still readable")
		}

		// Give the sweeper time to run, then inspect the store itself,
		// because Get() can't distinguish swept from unswept.
		time.Sleep(time.Minute)
		synctest.Wait()

		c.mu.Lock()
		defer c.mu.Unlock()

		if _, exists := c.buckets["bucket"].items["short"]; exists {
			t.Error("sweeper did not remove expired item from store")
		}
		if _, exists := c.buckets["bucket"].items["long"]; !exists {
			t.Error("sweeper removed a live item")
		}
		if got := c.buckets["bucket"].size; got != 2 {
			t.Errorf("bucket size = %d after sweep, want 2", got)
		}
		if _, exists := c.buckets["solo"]; exists {
			t.Error("bucket with no live fields was not removed")
		}
	})
}

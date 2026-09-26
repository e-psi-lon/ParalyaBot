package cache

import (
	"log"
	"slices"
	"sort"
	"sync"
	"time"
)

type Cache struct {
	mu             sync.RWMutex
	buckets        map[string]*bucket
	maxBucketSizes map[string]int64
	stopChan       chan struct{}
	touchChan      chan touchRequest
	touchDone      chan struct{}
	stopOnce       sync.Once
	stopStarted    bool
}

func NewCache() *Cache {
	cache := &Cache{
		buckets:        make(map[string]*bucket),
		maxBucketSizes: make(map[string]int64),
		stopChan:       make(chan struct{}),
		touchChan:      make(chan touchRequest, touchChanSize),
		touchDone:      make(chan struct{}),
	}
	go cache.janitor(0)
	go cache.touchLoop()
	return cache
}

func (i bucketItem) isExpired() bool {
	return !i.ExpiresAt.IsZero() && time.Now().After(i.ExpiresAt)
}

func cloneBytes(value []byte) []byte { return slices.Clone(value) }

func (c *Cache) newBucket(bucketName string) *bucket {
	b := &bucket{items: make(map[string]bucketItem)}
	c.buckets[bucketName] = b
	if _, ok := c.maxBucketSizes[bucketName]; !ok {
		c.maxBucketSizes[bucketName] = defaultMaxBucketBytes
	}
	return b
}

func (c *Cache) cleanup() {
	c.mu.Lock()
	defer c.mu.Unlock()
	for bucketName, cacheBucket := range c.buckets {
		for field, item := range cacheBucket.items {
			if item.isExpired() {
				c.deleteField(bucketName, field)
			}
		}
		c.evictBucket(bucketName)
	}
}

type evictionCandidate struct {
	field        string
	lastAccessed time.Time
}

func (c *Cache) evictBucket(bucketName string) {
	bucket, ok := c.buckets[bucketName]
	if !ok {
		return
	}
	maxBytes, ok := c.maxBucketSizes[bucketName]
	if !ok {
		return
	}
	if maxBytes <= 0 || bucket.size <= maxBytes {
		return
	}
	candidates := make([]evictionCandidate, 0, len(bucket.items))
	for field, item := range bucket.items {
		candidates = append(candidates, evictionCandidate{field: field, lastAccessed: item.LastAccessed})
	}
	sort.Slice(candidates, func(i, j int) bool { return candidates[i].lastAccessed.Before(candidates[j].lastAccessed) })
	for _, candidate := range candidates {
		if bucket.size <= maxBytes {
			return
		}
		c.deleteField(bucketName, candidate.field)
		bucket, ok = c.buckets[bucketName]
		if !ok {
			return
		}
	}
}

func (c *Cache) touchLoop() {
	defer close(c.touchDone)
	for request := range c.touchChan {
		c.touch(request.bucketName, request.field)
	}
}

func (c *Cache) requestTouch(bucketName, field string) {
	select {
	case c.touchChan <- touchRequest{bucketName: bucketName, field: field}:
	default:
	}
}

func (c *Cache) janitor(consecutiveFailures int) {
	defer func() {
		if recovered := recover(); recovered != nil {
			log.Printf("recovered panic in janitor (consecutive %d): %v", consecutiveFailures, recovered)
			if consecutiveFailures >= janitorConsecutiveFailureThreshold {
				log.Fatal("janitor failed too many times, exiting")
			}
			go c.janitor(consecutiveFailures + 1)
		}
	}()
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	successfulTicks := 0
	for {
		select {
		case <-ticker.C:
			c.cleanup()
			successfulTicks++
			if successfulTicks >= successfulTickThreshold {
				consecutiveFailures = 0
			}
		case <-c.stopChan:
			return
		}
	}
}

func (c *Cache) touch(bucketName, field string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if cacheBucket, ok := c.buckets[bucketName]; ok {
		if item, itemOk := cacheBucket.items[field]; itemOk {
			item.LastAccessed = time.Now()
			cacheBucket.items[field] = item
		}
	}
}

func (c *Cache) setField(bucketName, field string, item bucketItem) bool {
	cacheBucket := c.buckets[bucketName]
	if cacheBucket == nil {
		cacheBucket = c.newBucket(bucketName)
	}
	fieldExisted := false
	if old, existed := cacheBucket.items[field]; existed {
		cacheBucket.size -= int64(len(old.Value))
		fieldExisted = !old.isExpired()
	}
	cacheBucket.items[field] = item
	cacheBucket.size += int64(len(item.Value))
	return fieldExisted
}

func (c *Cache) deleteField(bucketName, field string) bool {
	cacheBucket := c.buckets[bucketName]
	if cacheBucket == nil {
		return false
	}
	item, ok := cacheBucket.items[field]
	if !ok {
		return false
	}
	cacheBucket.size -= int64(len(item.Value))
	delete(cacheBucket.items, field)
	if len(cacheBucket.items) == 0 {
		delete(c.buckets, bucketName)
	}
	return !item.isExpired()
}

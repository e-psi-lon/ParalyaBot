package cache

import "time"

func (c *Cache) Stop() {
	c.stopOnce.Do(func() {
		c.mu.Lock()
		c.stopStarted = true
		close(c.stopChan)
		close(c.touchChan)
		c.mu.Unlock()
	})
	<-c.touchDone
}

func (c *Cache) Set(bucketName, field string, value []byte) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.stopStarted {
		return false
	}
	return c.setField(bucketName, field, bucketItem{Value: cloneBytes(value), LastAccessed: time.Now()})
}

func (c *Cache) Get(bucketName, field string) ([]byte, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if c.stopStarted {
		return nil, false
	}
	cacheBucket, ok := c.buckets[bucketName]
	if !ok {
		return nil, false
	}
	item, ok := cacheBucket.items[field]
	if !ok || item.isExpired() {
		return nil, false
	}
	c.requestTouch(bucketName, field)
	return cloneBytes(item.Value), true
}

func (c *Cache) Vals(bucketName string) [][]byte {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if c.stopStarted {
		return nil
	}
	cacheBucket, ok := c.buckets[bucketName]
	if !ok {
		return nil
	}
	values := make([][]byte, 0, len(cacheBucket.items))
	for _, item := range cacheBucket.items {
		if !item.isExpired() {
			values = append(values, cloneBytes(item.Value))
		}
	}
	return values
}

func (c *Cache) Len(bucketName string) int {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if c.stopStarted {
		return 0
	}
	cacheBucket, ok := c.buckets[bucketName]
	if !ok {
		return 0
	}
	count := 0
	for _, item := range cacheBucket.items {
		if !item.isExpired() {
			count++
		}
	}
	return count
}

func (c *Cache) Delete(bucketName, field string) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.stopStarted {
		return false
	}
	return c.deleteField(bucketName, field)
}

func (c *Cache) DeleteBucket(bucketName string) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.stopStarted {
		return false
	}
	_, existed := c.buckets[bucketName]
	delete(c.buckets, bucketName)
	return existed
}

func (c *Cache) MemoryUsage(bucketName string) (int64, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if c.stopStarted {
		return 0, false
	}
	bucket, ok := c.buckets[bucketName]
	if !ok {
		return 0, false
	}
	return bucket.size, true
}

func (c *Cache) MemoryStats() (int64, int64) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if c.stopStarted {
		return 0, 0
	}
	var payloadBytes int64
	for _, bucket := range c.buckets {
		payloadBytes += bucket.size
	}
	return payloadBytes, int64(len(c.buckets))
}

func (c *Cache) SetMaxBucketSize(bucketName string, maxSize int64) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.stopStarted {
		return
	}
	c.maxBucketSizes[bucketName] = maxSize
}

func (c *Cache) GetMaxBucketSize(bucketName string) (int64, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if c.stopStarted {
		return 0, false
	}
	maxSize, ok := c.maxBucketSizes[bucketName]
	return maxSize, ok
}

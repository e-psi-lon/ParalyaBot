package cache

import "time"

type bucketItem struct {
	Value        []byte
	ExpiresAt    time.Time
	LastAccessed time.Time
}

type touchRequest struct {
	bucketName string
	field      string
}

type bucket struct {
	size  int64
	items map[string]bucketItem
}

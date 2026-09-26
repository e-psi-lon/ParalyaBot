package cache

import (
	"errors"
	"strings"
	"time"
)

type ExpireStatus int

const (
	StatusNoSuchField     ExpireStatus = -2
	StatusConditionNotMet ExpireStatus = 0
	StatusSet             ExpireStatus = 1
	StatusDeleted         ExpireStatus = 2
)

type ExpirationCondition int

const (
	NONE ExpirationCondition = iota
	NX
	XX
	GT
	LT
)

var expirationCondition = map[string]ExpirationCondition{"nx": NX, "xx": XX, "gt": GT, "lt": LT}
var ErrInvalidCondition = errors.New("cache: invalid condition")

func Condition(value string) (ExpirationCondition, error) {
	if condition, ok := expirationCondition[strings.ToLower(value)]; ok {
		return condition, nil
	}
	return NONE, ErrInvalidCondition
}

func (c *Cache) Expire(bucketName, field string, seconds int, condition ExpirationCondition) ExpireStatus {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.stopStarted || seconds > int(maxExpirationSeconds) {
		return StatusNoSuchField
	}
	cacheBucket, ok := c.buckets[bucketName]
	if !ok {
		return StatusNoSuchField
	}
	item, ok := cacheBucket.items[field]
	if !ok {
		return StatusNoSuchField
	}
	if item.isExpired() {
		c.deleteField(bucketName, field)
		return StatusNoSuchField
	}
	if seconds <= 0 {
		c.deleteField(bucketName, field)
		return StatusDeleted
	}
	if (condition == NX && !item.ExpiresAt.IsZero()) || (condition == XX && item.ExpiresAt.IsZero()) {
		return StatusConditionNotMet
	}
	newExpiry := time.Now().Add(time.Duration(seconds) * time.Second)
	if condition == GT && !item.ExpiresAt.IsZero() && !newExpiry.After(item.ExpiresAt) {
		return StatusConditionNotMet
	}
	if condition == LT && !item.ExpiresAt.IsZero() && !newExpiry.Before(item.ExpiresAt) {
		return StatusConditionNotMet
	}
	item.ExpiresAt = newExpiry
	cacheBucket.items[field] = item
	return StatusSet
}

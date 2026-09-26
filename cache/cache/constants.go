package cache

import "time"

const (
	successfulTickThreshold            = 5
	janitorConsecutiveFailureThreshold = 3
	touchChanSize                      = 256
	maxExpirationSeconds               = int64((1<<63 - 1) / int64(time.Second))
	defaultMaxBucketBytes              = int64(4 << 20)
)

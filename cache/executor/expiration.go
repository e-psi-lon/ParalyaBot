package executor

import (
	"strings"
	"time"

	"paralyabot-cache/cache"
	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
)

func handleHexpire(args [][]byte, c *cache.Cache) resp.Reply {
	seconds, reply := parseInt(args[1])
	if reply != nil {
		return reply
	}
	if seconds > int64((1<<63-1)/int64(time.Second)) {
		return redis.GenericError("invalid expire time")
	}
	index := 2
	condition := cache.NONE
	if parsed, err := cache.Condition(string(args[index])); err == nil {
		condition = parsed
		index++
	}
	if !strings.EqualFold(string(args[index]), "fields") {
		return redis.GenericError("syntax error")
	}
	if len(args) < index+2 {
		return redis.GenericError("wrong number of arguments")
	}
	fieldCount, reply := parseInt(args[index+1])
	if reply != nil {
		return reply
	}

	if fieldCount <= 0 || len(args[index+2:]) != int(fieldCount) {
		return redis.GenericError("wrong number of arguments")
	}

	replies := make([]resp.Reply, fieldCount)
	for i, field := range args[index+2:] {
		replies[i] = resp.Integer(int(c.Expire(string(args[0]), string(field), int(seconds), condition)))
	}
	return resp.ArrayReply(replies...)
}

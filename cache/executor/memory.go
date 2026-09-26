package executor

import (
	"paralyabot-cache/cache"
	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
)

func handleMemory(args [][]byte, c *cache.Cache) resp.Reply {
	subCommand, errReply := decodeSubcommand(args, redis.MemorySubCommandFromString, redis.MEMORY_STRING)
	if errReply != nil {
		return errReply
	}
	switch subCommand.Verb {
	case redis.MEMORY_USAGE:
		value, ok := c.MemoryUsage(string(subCommand.Args[0]))
		if !ok {
			return resp.BulkString(nil)
		}
		return resp.Integer64(value)
	case redis.MEMORY_STATS:
		return memoryStats(c)
	case redis.InvalidMemorySubCommand:
		panic("unreachable")
	default:
		panic("unreachable")
	}
}

func memoryStats(c *cache.Cache) resp.Reply {
	payload, keys := c.MemoryStats()
	metrics := []namedReply{
		{"peak.allocated", resp.Integer64(payload)},
		{"total.allocated", resp.Integer64(payload)},
		{"startup.allocated", resp.Integer64(0)},
		{"replication.backlog", resp.Integer64(0)},
		{"clients.slaves", resp.Integer64(0)},
		{"clients.normal", resp.Integer64(0)},
		{"cluster.links", resp.Integer64(0)},
		{"aof.buffer", resp.Integer64(0)},
		{"lua.caches", resp.Integer64(0)},
		{"functions.caches", resp.Integer64(0)},
		{"overhead.total", resp.Integer64(0)},
		{"keys.count", resp.Integer64(keys)},
		{"keys.bytes-per-key", resp.Integer64(average(payload, keys))},
		{"dataset.bytes", resp.Integer64(payload)},
		{"dataset.percentage", resp.Integer64(percentage(payload, payload))},
		{"peak.percentage", resp.Integer64(percentage(payload, payload))},
		{"allocator.allocated", resp.Integer64(payload)},
		{"allocator.active", resp.Integer64(payload)},
		{"allocator.resident", resp.Integer64(payload)},
		{"allocator-fragmentation.ratio", resp.Integer64(1)},
		{"allocator-fragmentation.bytes", resp.Integer64(0)},
		{"allocator-rss.ratio", resp.Integer64(1)},
		{"allocator-rss.bytes", resp.Integer64(0)},
		{"rss-overhead.ratio", resp.Integer64(1)},
		{"rss-overhead.bytes", resp.Integer64(0)},
		{"fragmentation", resp.Integer64(1)},
		{"fragmentation.bytes", resp.Integer64(0)},
	}
	return namedReplyArray(metrics...)
}
func average(payload, keys int64) int64 {
	if keys == 0 {
		return 0
	}
	return payload / keys
}
func percentage(value, total int64) int64 {
	if total == 0 {
		return 0
	}
	return value * 100 / total
}

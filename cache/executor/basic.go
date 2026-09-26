package executor

import (
	"strconv"

	"paralyabot-cache/cache"
	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
)

func handleDel(args [][]byte, c *cache.Cache) resp.Reply {
	count := 0
	for _, key := range args {
		if c.DeleteBucket(string(key)) {
			count++
		}
	}
	return resp.Integer(count)
}

func handlePing(args [][]byte) resp.Reply {
	if len(args) == 0 {
		return resp.SimpleString("PONG")
	}
	if len(args) != 1 {
		return redis.GenericError("wrong number of arguments for 'ping' command")
	}
	return resp.BulkString(args[0])
}

func handleHello(args [][]byte, clientID int64) resp.Reply {
	if len(args) > 0 {
		version, err := strconv.Atoi(string(args[0]))
		if err != nil {
			return redis.GenericError("value is not an integer or out of range")
		}
		if version != 2 {
			return redis.NoProtoError("unsupported protocol version")
		}
		if len(args) > 1 {
			return redis.GenericError("Syntax error in HELLO option '%s'", string(args[1]))
		}
	}
	return namedReplyArray(
		namedReply{"server", resp.BulkString([]byte("ParalyaBot Cache"))},
		namedReply{"version", resp.BulkString([]byte("1.0.0"))},
		namedReply{"proto", resp.Integer64(2)},
		namedReply{"id", resp.Integer64(clientID)},
		namedReply{"mode", resp.BulkString([]byte("standalone"))},
		namedReply{"role", resp.BulkString([]byte("master"))},
		namedReply{"modules", resp.ArrayReply()},
	)
}

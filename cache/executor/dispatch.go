package executor

import (
	"paralyabot-cache/cache"
	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
)

func Execute(command *redis.Command, c *cache.Cache, clientID int64, clients *Clients) resp.Reply {
	switch command.Verb {
	case redis.HSET:
		return handleHSet(command.Args, c)
	case redis.HGET:
		return handleHGet(command.Args, c)
	case redis.HMGET:
		return handleHMGet(command.Args, c)
	case redis.HVALS:
		return handleHVals(command.Args, c)
	case redis.HLEN:
		return handleHLen(command.Args, c)
	case redis.HDEL:
		return handleHDel(command.Args, c)
	case redis.HEXPIRE:
		return handleHexpire(command.Args, c)
	case redis.DEL:
		return handleDel(command.Args, c)
	case redis.PING:
		return handlePing(command.Args)
	case redis.CLIENT:
		return handleClient(command.Args, clientID, clients)
	case redis.HELLO:
		return handleHello(command.Args, clientID)
	case redis.MEMORY:
		return handleMemory(command.Args, c)
	case redis.CONFIG:
		return handleConfig(command.Args, c)
	case redis.Invalid:
		panic("unreachable")
	default:
		panic("unreachable")
	}
}

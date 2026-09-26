package executor

import (
	"paralyabot-cache/cache"
	"paralyabot-cache/resp"
)

func handleHSet(args [][]byte, c *cache.Cache) resp.Reply {
	if c.Set(string(args[0]), string(args[1]), args[2]) {
		return resp.Integer(0)
	}
	return resp.Integer(1)
}

func handleHGet(args [][]byte, c *cache.Cache) resp.Reply {
	value, ok := c.Get(string(args[0]), string(args[1]))
	if !ok {
		return resp.BulkString(nil)
	}
	return resp.BulkString(value)
}

func handleHMGet(args [][]byte, c *cache.Cache) resp.Reply {
	values := make([]resp.Reply, len(args)-1)
	for i, field := range args[1:] {
		value, _ := c.Get(string(args[0]), string(field))
		values[i] = resp.BulkString(value)
	}
	return resp.ArrayReply(values...)
}

func handleHVals(args [][]byte, c *cache.Cache) resp.Reply {
	values := c.Vals(string(args[0]))
	replies := make([]resp.Reply, len(values))
	for i, value := range values {
		replies[i] = resp.BulkString(value)
	}
	return resp.ArrayReply(replies...)
}

func handleHLen(args [][]byte, c *cache.Cache) resp.Reply {
	return resp.Integer(c.Len(string(args[0])))
}

func handleHDel(args [][]byte, c *cache.Cache) resp.Reply {
	count := 0
	for _, field := range args[1:] {
		if c.Delete(string(args[0]), string(field)) {
			count++
		}
	}
	return resp.Integer(count)
}

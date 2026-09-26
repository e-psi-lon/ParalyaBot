package executor

import (
	"strconv"
	"strings"

	"paralyabot-cache/cache"
	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
)

func handleConfig(args [][]byte, c *cache.Cache) resp.Reply {
	subCommand, errReply := decodeSubcommand(args, redis.ConfigSubCommandFromString, redis.CONFIG_STRING)
	if errReply != nil {
		return errReply
	}
	switch subCommand.Verb {
	case redis.CONFIG_GET:
		return handleConfigGet(subCommand.Args, c)
	case redis.CONFIG_SET:
		return handleConfigSet(subCommand.Args, c)
	case redis.InvalidConfigSubCommand:
		panic("unreachable")
	default:
		panic("unreachable")
	}
}

func handleConfigGet(args [][]byte, c *cache.Cache) resp.Reply {
	firstArg := args[0]
	if bucketName, ok := strings.CutSuffix(string(firstArg), ":maxmemory"); ok {
		if maxSize, ok := c.GetMaxBucketSize(bucketName); ok {
			return resp.ArrayReply(
				resp.BulkString(firstArg),
				resp.BulkString([]byte(strconv.FormatInt(maxSize, 10))),
			)
		}
	}
	return resp.ArrayReply()
}

func handleConfigSet(args [][]byte, c *cache.Cache) resp.Reply {
	firstArg := args[0]
	if bucketName, ok := strings.CutSuffix(string(firstArg), ":maxmemory"); ok {
		maxSize, err := strconv.ParseInt(string(args[1]), 10, 64)
		if err != nil {
			return redis.GenericError("value is not an integer or out of range")
		}
		if maxSize < 0 {
			return redis.GenericError("maxmemory must be a non-negative integer")
		}
		c.SetMaxBucketSize(bucketName, maxSize)
		return resp.SimpleString("OK")
	}
	return redis.GenericError("unknown CONFIG SET parameter")
}

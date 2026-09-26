package executor

import (
	"errors"
	"strconv"

	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
)

type namedReply struct {
	name  string
	value resp.Reply
}

func namedReplyArray(pairs ...namedReply) resp.Reply {
	values := make([]resp.Reply, 0, len(pairs)*2)
	for _, pair := range pairs {
		values = append(values, resp.BulkString([]byte(pair.name)), pair.value)
	}
	return resp.ArrayReply(values...)
}

func parseInt(value []byte) (int64, resp.Reply) {
	parsed, err := strconv.ParseInt(string(value), 10, 64)
	if err != nil {
		return 0, redis.GenericError("value is not an integer or out of range")
	}
	return parsed, nil
}

func decodeSubcommand[T redis.Verb](args [][]byte, decoder redis.Decoder[T], verbString string) (redis.Invocation[T], resp.Reply) {
	subcommand, err := redis.InterpretSubCommand(args, decoder)
	if err != nil {
		if unknownErr, ok := errors.AsType[*redis.UnknownSubVerbError](err); ok {
			return subcommand, redis.UnknownSubCommandError(verbString, unknownErr.Verb)
		}
		if arityErr, ok := errors.AsType[*redis.ArityError[T]](err); ok {
			return subcommand, redis.GenericError("wrong number of arguments for %s|%s command", verbString, arityErr.Verb.String())
		}
		if errors.Is(err, redis.ErrEmpty) {
			return subcommand, redis.GenericError("wrong number of arguments for '%s' command", verbString)
		}
	}
	return subcommand, nil
}

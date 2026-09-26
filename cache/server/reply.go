package server

import (
	"bufio"
	"errors"
	"log"
	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
)

func errorReplyForInterpret(err error) resp.Reply {
	if unknownErr, ok := errors.AsType[*redis.UnknownVerbError](err); ok {
		return redis.GenericError("unknown command %q", unknownErr.Verb)
	}
	if arityErr, ok := errors.AsType[*redis.ArityError[redis.CommandType]](err); ok {
		return redis.GenericError("wrong number of arguments for %q command", arityErr.Verb.String())
	}
	return redis.GenericError("protocol error")
}

func writeAndFlush(id int64, writer *bufio.Writer, reply resp.Reply, context string) error {
	if err := resp.WriteReply(writer, reply); err != nil {
		log.Printf("connection %d write %s failed: %v", id, context, err)
		return err
	}
	if err := writer.Flush(); err != nil {
		log.Printf("connection %d flush %s failed: %v", id, context, err)
		return err
	}
	return nil
}

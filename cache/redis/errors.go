package redis

import (
	"errors"
	"fmt"
	"paralyabot-cache/resp"
)

var ErrEmpty = errors.New("command: empty command")

type ArityError[T Verb] struct {
	Verb T
	Got  int
}

func (e *ArityError[T]) Error() string {
	return fmt.Sprintf("command: wrong number of arguments for %q (got %d, expected %d)", e.Verb, e.Got, e.Verb.Arity())
}

type UnknownVerbError struct{ Verb string }

func (e *UnknownVerbError) Error() string { return fmt.Sprintf("command: unknown command %q", e.Verb) }

type UnknownSubVerbError struct {
	UnknownVerbError
	Parent string
}

func (e *UnknownSubVerbError) Error() string {
	return fmt.Sprintf("command: unknown %s subcommand for %s", e.Verb, e.Parent)
}

func (e *UnknownSubVerbError) As(target any) bool {
	if base, ok := target.(**UnknownVerbError); ok {
		*base = &e.UnknownVerbError
		return true
	}
	return false
}

func GenericError(format string, args ...any) resp.Reply {
	return resp.Error(fmt.Sprintf("ERR "+format, args...))
}

func NoProtoError(format string, args ...any) resp.Reply {
	return resp.Error(fmt.Sprintf("NOPROTO "+format, args...))
}

func UnknownSubCommandError(topLevelCommand string, subCommand string) resp.Reply {
	return GenericError("unknown subcommand '%s'. Try %s HELP.", subCommand, topLevelCommand)
}

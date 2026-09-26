package redis

import (
	"slices"
	"strings"
)

type Verb interface {
	comparable
	String() string
	Arity() int
	IsVariadic() bool
}

type Invocation[T Verb] struct {
	Verb T
	Args [][]byte
}

type CommandType int

type Command = Invocation[CommandType]
type SubCommand[T Verb] = Invocation[T]

const (
	Invalid CommandType = iota
	HSET
	HGET
	HMGET
	HVALS
	HLEN
	HDEL
	HEXPIRE
	DEL
	PING
	CLIENT
	HELLO
	MEMORY
	CONFIG
)

var names = map[CommandType]string{
	Invalid: "InvalidCommand",
	HSET:    "HSET",
	HGET:    "HGET",
	HMGET:   "HMGET",
	HVALS:   "HVALS",
	HLEN:    "HLEN",
	HDEL:    "HDEL",
	HEXPIRE: "HEXPIRE",
	DEL:     "DEL",
	PING:    "PING",
	CLIENT:  CLIENT_STRING,
	HELLO:   "HELLO",
	MEMORY:  MEMORY_STRING,
	CONFIG:  CONFIG_STRING,
}
var types = map[string]CommandType{
	"HSET":        HSET,
	"HGET":        HGET,
	"HMGET":       HMGET,
	"HVALS":       HVALS,
	"HLEN":        HLEN,
	"HDEL":        HDEL,
	"HEXPIRE":     HEXPIRE,
	"DEL":         DEL,
	"PING":        PING,
	CLIENT_STRING: CLIENT,
	"HELLO":       HELLO,
	MEMORY_STRING: MEMORY,
	CONFIG_STRING: CONFIG,
}
var arities = map[CommandType]int{
	HSET:    3,
	HGET:    2,
	HMGET:   2,
	HVALS:   1,
	HLEN:    1,
	HDEL:    2,
	HEXPIRE: 4,
	DEL:     1,
	PING:    0,
	CLIENT:  1,
	HELLO:   0,
	MEMORY:  1,
	CONFIG:  1,
}
var variadic = []CommandType{HMGET, HDEL, HEXPIRE, DEL, PING, CLIENT, HELLO, MEMORY, CONFIG}

func (t CommandType) String() string   { return names[t] }
func (t CommandType) Arity() int       { return arities[t] }
func (t CommandType) IsVariadic() bool { return slices.Contains(variadic, t) }

type Decoder[T Verb] func(string) (T, error)

func interpret[T Verb](fields [][]byte, decoder Decoder[T]) (Invocation[T], error) {
	if len(fields) == 0 {
		return Invocation[T]{}, ErrEmpty
	}
	verbText := strings.ToUpper(string(fields[0]))
	verb, err := decoder(verbText)
	if err != nil {
		return Invocation[T]{}, err
	}
	args := fields[1:]
	if (verb.IsVariadic() && len(args) < verb.Arity()) || (!verb.IsVariadic() && len(args) != verb.Arity()) {
		return Invocation[T]{}, &ArityError[T]{Verb: verb, Got: len(args)}
	}
	return Invocation[T]{Verb: verb, Args: args}, nil
}

func FromString(value string) (CommandType, error) {
	if t, ok := types[value]; ok {
		return t, nil
	}
	return Invalid, &UnknownVerbError{Verb: value}
}

func Interpret(fields [][]byte) (Command, error) { return interpret(fields, FromString) }

func InterpretSubCommand[T Verb](fields [][]byte, decoder Decoder[T]) (SubCommand[T], error) {
	return interpret(fields, decoder)
}

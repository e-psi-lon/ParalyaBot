package redis

type MemorySubCommandType int

const MEMORY_STRING = "MEMORY"

const (
	InvalidMemorySubCommand MemorySubCommandType = iota
	MEMORY_USAGE
	MEMORY_STATS
)

var memorySubCommands = map[string]MemorySubCommandType{"USAGE": MEMORY_USAGE, "STATS": MEMORY_STATS}
var memorySubCommandStrings = map[MemorySubCommandType]string{MEMORY_USAGE: "USAGE", MEMORY_STATS: "STATS"}
var memorySubCommandArity = map[MemorySubCommandType]int{MEMORY_USAGE: 1, MEMORY_STATS: 0}

func MemorySubCommandFromString(value string) (MemorySubCommandType, error) {
	if c, ok := memorySubCommands[value]; ok {
		return c, nil
	}
	return InvalidMemorySubCommand, &UnknownSubVerbError{
		UnknownVerbError: UnknownVerbError{Verb: value},
		Parent:           MEMORY_STRING,
	}
}
func (c MemorySubCommandType) String() string {
	if s, ok := memorySubCommandStrings[c]; ok {
		return s
	}
	return "InvalidMemorySubCommand"
}
func (c MemorySubCommandType) IsVariadic() bool { return false }
func (c MemorySubCommandType) Arity() int {
	if a, ok := memorySubCommandArity[c]; ok {
		return a
	}
	return 0
}

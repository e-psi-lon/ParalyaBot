package redis

type ConfigSubCommandType int

const CONFIG_STRING = "CONFIG"

const (
	InvalidConfigSubCommand ConfigSubCommandType = iota
	CONFIG_GET
	CONFIG_SET
)

var configSubCommands = map[string]ConfigSubCommandType{"GET": CONFIG_GET, "SET": CONFIG_SET}

func ConfigSubCommandFromString(value string) (ConfigSubCommandType, error) {
	if c, ok := configSubCommands[value]; ok {
		return c, nil
	}
	return InvalidConfigSubCommand, &UnknownSubVerbError{
		UnknownVerbError: UnknownVerbError{Verb: value},
		Parent:           CONFIG_STRING,
	}
}
func (c ConfigSubCommandType) String() string {
	switch c {
	case CONFIG_GET:
		return "GET"
	case CONFIG_SET:
		return "SET"
	case InvalidConfigSubCommand:
		return "InvalidConfigSubCommand"
	default:
		return "InvalidConfigSubCommand"
	}
}

func (c ConfigSubCommandType) IsVariadic() bool { return false }
func (c ConfigSubCommandType) Arity() int {
	switch c {
	case CONFIG_GET:
		return 1
	case CONFIG_SET:
		return 2
	case InvalidConfigSubCommand:
		return 0
	default:
		return 0
	}
}

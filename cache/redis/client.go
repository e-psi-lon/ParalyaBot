package redis

type ClientSubCommandType int

const CLIENT_STRING = "CLIENT"

const (
	InvalidClientSubCommand ClientSubCommandType = iota
	CLIENT_SETINFO
	CLIENT_SETNAME
)

var clientSubCommands = map[string]ClientSubCommandType{"SETINFO": CLIENT_SETINFO, "SETNAME": CLIENT_SETNAME}
var clientSubCommandStrings = map[ClientSubCommandType]string{CLIENT_SETINFO: "SETINFO", CLIENT_SETNAME: "SETNAME"}
var clientSubCommandArity = map[ClientSubCommandType]int{CLIENT_SETINFO: 2, CLIENT_SETNAME: 1}

func ClientSubCommandFromString(value string) (ClientSubCommandType, error) {
	if c, ok := clientSubCommands[value]; ok {
		return c, nil
	}
	return InvalidClientSubCommand, &UnknownSubVerbError{
		UnknownVerbError: UnknownVerbError{Verb: value},
		Parent:           CLIENT_STRING,
	}
}
func (c ClientSubCommandType) String() string {
	if s, ok := clientSubCommandStrings[c]; ok {
		return s
	}
	return "InvalidClientSubCommand"
}
func (c ClientSubCommandType) IsVariadic() bool { return false }
func (c ClientSubCommandType) Arity() int {
	if a, ok := clientSubCommandArity[c]; ok {
		return a
	}
	return 0
}

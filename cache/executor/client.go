package executor

import (
	"strings"

	"paralyabot-cache/redis"
	"paralyabot-cache/resp"
)

func handleClient(args [][]byte, clientID int64, clients *Clients) resp.Reply {
	subCommand, errReply := decodeSubcommand(args, redis.ClientSubCommandFromString, redis.CLIENT_STRING)
	if errReply != nil {
		return errReply
	}
	switch subCommand.Verb {
	case redis.CLIENT_SETINFO:
		switch strings.ToLower(string(subCommand.Args[0])) {
		case "lib-name":
			clients.Modify(clientID, func(info ClientInfo) ClientInfo {
				info.LibName = string(subCommand.Args[1])
				return info
			})
		case "lib-ver":
			clients.Modify(clientID, func(info ClientInfo) ClientInfo {
				info.LibVer = string(subCommand.Args[1])
				return info
			})
		default:
			return redis.GenericError("Unknown subcommand or wrong number of arguments for 'SETINFO'. Try CLIENT HELP.")
		}
		return resp.SimpleString("OK")
	case redis.CLIENT_SETNAME:
		clients.Modify(clientID, func(info ClientInfo) ClientInfo {
			info.Name = string(subCommand.Args[0])
			return info
		})
		return resp.SimpleString("OK")
	case redis.InvalidClientSubCommand:
		panic("unreachable")
	default:
		panic("unreachable")
	}
}

package redis

import (
	"testing"
)

func TestGenericError(t *testing.T) {
	tests := []struct {
		format string
		args   []any
		want   string
	}{
		{"bad argument", nil, "-ERR bad argument\r\n"},
		{"wrong number of arguments for '%s' command", []any{"get"}, "-ERR wrong number of arguments for 'get' command\r\n"},
		{"%d is out of range", []any{42}, "-ERR 42 is out of range\r\n"},
	}
	for _, tt := range tests {
		got := string(GenericError(tt.format, tt.args...))
		if got != tt.want {
			t.Errorf("GenericError(%q, %v) = %q, want %q", tt.format, tt.args, got, tt.want)
		}
	}
}

func TestNoProtoError(t *testing.T) {
	tests := []struct {
		format string
		args   []any
		want   string
	}{
		{"unsupported protocol version", nil, "-NOPROTO unsupported protocol version\r\n"},
		{"unsupported protocol version %d", []any{4}, "-NOPROTO unsupported protocol version 4\r\n"},
	}
	for _, tt := range tests {
		got := string(NoProtoError(tt.format, tt.args...))
		if got != tt.want {
			t.Errorf("NoProtoError(%q, %v) = %q, want %q", tt.format, tt.args, got, tt.want)
		}
	}
}

func TestUnknownSubVerbError_As_WrongTarget(t *testing.T) {
	e := &UnknownSubVerbError{}
	var wrongTarget *ArityError[ClientSubCommandType]
	if e.As(&wrongTarget) {
		t.Fatal("As() = true for mismatched target type, want false")
	}
}

package resp

import (
	"errors"
	"fmt"
)

var (
	ErrLineTooLong          = errors.New("resp: line too long")
	ErrLineMissingCRLF      = errors.New("resp: line missing CRLF terminator")
	ErrBulkTerminatorCRLF   = errors.New("resp: bad bulk string terminator")
	ErrInvalidArrayLength   = errors.New("resp: invalid array length")
	ErrTooManyArrayElements = errors.New("resp: too many array elements")
	ErrInvalidBulkLength    = errors.New("resp: invalid bulk string length")
	ErrBulkStringTooLarge   = errors.New("resp: bulk string too large")
	ErrBadPrefix            = errors.New("resp: unexpected type prefix")
	ErrEmptyPrefixedInt     = errors.New("resp: empty prefixed integer")
)

func unexpectedByte(sentinel error, expected, got byte) error {
	return fmt.Errorf("%w (expected %q, got %q)", sentinel, expected, got)
}

func invalidNumberValue(sentinel error, value int) error {
	return fmt.Errorf("%w (got %d)", sentinel, value)
}

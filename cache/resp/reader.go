package resp

import (
	"bufio"
	"errors"
	"io"
	"strconv"
)

const (
	maxLineBytes      = 2 << 20
	maxBulkStringSize = 2 << 20
	maxArrayElements  = 1024
)

func readLine(r *bufio.Reader) ([]byte, error) {
	var line []byte
	for {
		part, err := r.ReadSlice('\n')
		line = append(line, part...)
		if len(line) > maxLineBytes {
			return nil, ErrLineTooLong
		}
		if err == nil {
			break
		}
		if !errors.Is(err, bufio.ErrBufferFull) {
			return nil, err
		}
	}
	if len(line) < 2 || line[len(line)-2] != '\r' {
		return nil, ErrLineMissingCRLF
	}
	return line[:len(line)-2], nil
}

func readBytes(r *bufio.Reader, n int) ([]byte, error) {
	buf := make([]byte, n)
	if _, err := io.ReadFull(r, buf); err != nil {
		return nil, err
	}
	return buf, nil
}

func expectCRLF(r *bufio.Reader) error {
	if char, err := r.ReadByte(); err != nil {
		return err
	} else if char != '\r' {
		return unexpectedByte(ErrBulkTerminatorCRLF, '\r', char)
	}
	if char, err := r.ReadByte(); err != nil {
		return err
	} else if char != '\n' {
		return unexpectedByte(ErrBulkTerminatorCRLF, '\n', char)
	}
	return nil
}

func readPrefixedInt(line []byte, expected byte) (int, error) {
	if len(line) == 0 {
		return 0, ErrEmptyPrefixedInt
	}
	prefix := line[0]
	if prefix != expected {
		return 0, unexpectedByte(ErrBadPrefix, expected, prefix)
	}
	return strconv.Atoi(string(line[1:]))
}

func readBulkString(r *bufio.Reader) ([]byte, error) {
	line, err := readLine(r)
	if err != nil {
		return nil, err
	}
	length, err := readPrefixedInt(line, respBulk)
	if err != nil {
		return nil, err
	}
	if length < -1 {
		return nil, invalidNumberValue(ErrInvalidBulkLength, length)
	}
	if length == -1 {
		return nil, nil
	}
	if length > maxBulkStringSize {
		return nil, invalidNumberValue(ErrBulkStringTooLarge, length)
	}
	bytes, err := readBytes(r, length)
	if err != nil {
		return nil, err
	}
	if crlfErr := expectCRLF(r); crlfErr != nil {
		return nil, crlfErr
	}
	return bytes, nil
}

func ReadRequest(r *bufio.Reader) ([][]byte, error) {
	firstLine, err := readLine(r)
	if err != nil {
		return nil, err
	}
	length, err := readPrefixedInt(firstLine, respArray)
	if err != nil {
		return nil, err
	}
	if length < 0 {
		return nil, ErrInvalidArrayLength
	}
	if length > maxArrayElements {
		return nil, invalidNumberValue(ErrTooManyArrayElements, length)
	}
	requests := make([][]byte, length)
	for i := range length {
		bulkString, bulkErr := readBulkString(r)
		if bulkErr != nil {
			return nil, bulkErr
		}
		requests[i] = bulkString
	}
	return requests, nil
}

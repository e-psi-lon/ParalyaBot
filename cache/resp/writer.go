package resp

import (
	"bufio"
	"strconv"
)

type Reply []byte

func appendElement(buffer []byte, elemType byte, b []byte) []byte {
	buffer = append(buffer, elemType)
	buffer = append(buffer, b...)
	buffer = append(buffer, eol...)
	return buffer
}

func SimpleString(s string) Reply {
	return appendElement(nil, respString, []byte(s))
}

func Error(msg string) Reply {
	return appendElement(nil, respError, []byte(msg))
}

func Integer(i int) Reply {
	return appendElement(nil, respInteger, []byte(strconv.Itoa(i)))
}

func Integer64(i int64) Reply {
	return appendElement(nil, respInteger, []byte(strconv.FormatInt(i, 10)))
}

func BulkString(s []byte) Reply {
	if s == nil {
		return appendElement(nil, respBulk, []byte("-1"))
	}
	lengthDigits := strconv.Itoa(len(s))
	buffer := make(Reply, 0, len(s)+len(lengthDigits)+5)
	buffer = appendElement(buffer, respBulk, []byte(lengthDigits))
	buffer = append(buffer, s...)
	buffer = append(buffer, eol...)
	return buffer
}

func ArrayReply(elements ...Reply) Reply {
	total := 0
	for _, elem := range elements {
		total += len(elem)
	}
	lengthDigits := len(strconv.Itoa(len(elements)))
	buffer := make(Reply, 0, 1+lengthDigits+2+total)
	buffer = appendElement(buffer, respArray, []byte(strconv.Itoa(len(elements))))
	for _, elem := range elements {
		buffer = append(buffer, elem...)
	}
	return buffer
}

func NilArrayReply() Reply {
	buffer := make(Reply, 0, 5)
	buffer = appendElement(buffer, respArray, []byte("-1"))
	return buffer
}

func WriteReply(w *bufio.Writer, r Reply) error {
	_, err := w.Write(r)
	if err != nil {
		return err
	}
	return nil
}

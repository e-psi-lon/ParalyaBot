//nolint:goconst
package resp

import (
	"bufio"
	"bytes"
	"runtime"
	"testing"
)

func FuzzReadRequest(f *testing.F) {
	// Seeds: valid frames, plus the hostile shapes worth mutating from.
	seeds := []string{
		"*1\r\n$4\r\nPING\r\n",
		"*3\r\n$4\r\nHSET\r\n$1\r\nb\r\n$1\r\nf\r\n",
		"*0\r\n",
		"*-1\r\n",
		"*1\r\n$-1\r\n",
		"*1\r\n$0\r\n\r\n",
		"*2\r\n$4\r\nPING\r\n",      // truncated: promises 2, gives 1
		"*1\r\n$10\r\nshort\r\n",    // bulk length longer than data
		"*1\r\n$1000000000\r\n",     // huge declared bulk length, no data
		"*1000000000\r\n",           // huge declared array length
		"*99999999999999999999\r\n", // integer overflow
		"*1\r\n$4\r\nPING",          // missing final CRLF
		"*1\r\n$4\r\nPING\n",        // bare LF
		"*1\r\n*1\r\n$1\r\na\r\n",   // nested array
		"*a\r\n",                    // non-numeric length
		"\r\n",
		"",
	}
	for _, s := range seeds {
		f.Add([]byte(s))
	}

	f.Fuzz(func(t *testing.T, data []byte) {
		reader := bufio.NewReader(bytes.NewReader(data))

		// The property: never panic, never hang, and never return a
		// result whose size exceeds what the input could have supplied.
		fields, err := ReadRequest(reader)
		if err != nil {
			return
		}

		total := 0
		for _, field := range fields {
			total += len(field)
		}
		if total > len(data) {
			t.Fatalf("parsed %d bytes of payload from %d bytes of input", total, len(data))
		}
	})
}

func TestReadRequestDoesNotPreallocateDeclaredLength(t *testing.T) {
	input := []byte("*1\r\n$1000000000\r\n")
	var before, after runtime.MemStats
	runtime.GC()
	runtime.ReadMemStats(&before)
	_, _ = ReadRequest(bufio.NewReader(bytes.NewReader(input)))
	runtime.ReadMemStats(&after)
	if grew := after.TotalAlloc - before.TotalAlloc; grew > 10<<20 {
		t.Fatalf("parsing a 17-byte input allocated %d bytes", grew)
	}
}

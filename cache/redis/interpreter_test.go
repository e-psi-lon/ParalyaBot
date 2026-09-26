package redis

import (
	"errors"
	"reflect"
	"testing"
)

type interpretTestCase[T Verb] struct {
	name        string
	fields      [][]byte
	wantVerb    T
	wantArgs    [][]byte
	wantErr     error  // sentinel-level errors (e.g. ErrEmpty), checked with errors.Is
	wantUnknown string // non-empty means expect *UnknownVerbError with this Verb text
	wantArity   bool   // true means expect *ArityError
	wantGot     int    // ArityError.Got, only checked when wantArity
}

func runInterpretTestCases[T Verb](t *testing.T, decoder Decoder[T], testCases []interpretTestCase[T]) {
	t.Helper()
	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			invocation, err := interpret(testCase.fields, decoder)

			if testCase.wantErr != nil {
				if !errors.Is(err, testCase.wantErr) {
					t.Fatalf("interpret() error = %v, want %v", err, testCase.wantErr)
				}
				return
			}
			if testCase.wantUnknown != "" {
				var unknownErr *UnknownVerbError
				if !errors.As(err, &unknownErr) {
					t.Fatalf("interpret() error = %v, want UnknownVerbError", err)
				}
				if unknownErr.Verb != testCase.wantUnknown {
					t.Fatalf("UnknownVerbError.Verb = %q, want %q", unknownErr.Verb, testCase.wantUnknown)
				}
				return
			}
			if testCase.wantArity {
				var arityErr *ArityError[T]
				if !errors.As(err, &arityErr) {
					t.Fatalf("interpret() error = %v, want ArityError", err)
				}
				if arityErr.Verb != testCase.wantVerb {
					t.Fatalf("ArityError.Verb = %v, want %v", arityErr.Verb, testCase.wantVerb)
				}
				if arityErr.Got != testCase.wantGot {
					t.Fatalf("ArityError.Got = %d, want %d", arityErr.Got, testCase.wantGot)
				}
				return
			}
			if err != nil {
				t.Fatalf("interpret() unexpected error: %v", err)
			}
			if invocation.Verb != testCase.wantVerb {
				t.Fatalf("interpret() verb = %v, want %v", invocation.Verb, testCase.wantVerb)
			}
			if !reflect.DeepEqual(invocation.Args, testCase.wantArgs) {
				t.Fatalf("interpret() args = %q, want %q", invocation.Args, testCase.wantArgs)
			}
		})
	}
}

func TestInterpret(t *testing.T) {
	runInterpretTestCases(t, FromString, []interpretTestCase[CommandType]{
		{
			name:     "normalizes command name",
			fields:   [][]byte{[]byte("hget"), []byte("bucket"), []byte("field")},
			wantVerb: HGET,
			wantArgs: [][]byte{[]byte("bucket"), []byte("field")},
		},
		{
			name:     "accepts variadic command minimum",
			fields:   [][]byte{[]byte("DEL"), []byte("bucket")},
			wantVerb: DEL,
			wantArgs: [][]byte{[]byte("bucket")},
		},
		{
			name:    "empty request",
			wantErr: ErrEmpty,
		},
		{
			name:        "unknown command",
			fields:      [][]byte{[]byte("wat")},
			wantUnknown: "WAT",
		},
		{
			name:      "too few exact arguments",
			fields:    [][]byte{[]byte("HGET"), []byte("bucket")},
			wantVerb:  HGET,
			wantArity: true,
			wantGot:   1,
		},
		{
			name:      "too many exact arguments",
			fields:    [][]byte{[]byte("HGET"), []byte("bucket"), []byte("field"), []byte("extra")},
			wantVerb:  HGET,
			wantArity: true,
			wantGot:   3,
		},
		{
			name:      "too few variadic arguments",
			fields:    [][]byte{[]byte("DEL")},
			wantVerb:  DEL,
			wantArity: true,
			wantGot:   0,
		},
	})
}

func TestInterpretSubCommand(t *testing.T) {
	runInterpretTestCases(t, ClientSubCommandFromString, []interpretTestCase[ClientSubCommandType]{
		{
			name:     "normalizes subcommand name",
			fields:   [][]byte{[]byte("setname"), []byte("client")},
			wantVerb: CLIENT_SETNAME,
			wantArgs: [][]byte{[]byte("client")},
		},
		{
			name:        "rejects unknown subcommand",
			fields:      [][]byte{[]byte("unknown"), []byte("value")},
			wantUnknown: "UNKNOWN",
		},
		{
			name:      "rejects wrong arity",
			fields:    [][]byte{[]byte("SETNAME")},
			wantVerb:  CLIENT_SETNAME,
			wantArity: true,
			wantGot:   0,
		},
	})
}

package server

import (
	"testing"
	"time"
)

func TestNextAcceptBackoff(t *testing.T) {
	cases := []struct {
		name string
		in   time.Duration
		want time.Duration
	}{
		{"zero starts at minimum", 0, minAcceptBackoff},
		{"negative treated as zero", -1, minAcceptBackoff},
		{"doubles", 10 * time.Millisecond, 20 * time.Millisecond},
		{"caps at maximum", 800 * time.Millisecond, maxAcceptBackoff},
		{"stays at cap", maxAcceptBackoff, maxAcceptBackoff},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := nextAcceptBackoff(tc.in)
			if got != tc.want {
				t.Errorf("nextAcceptBackoff(%v) = %v, want %v", tc.in, got, tc.want)
			}
		})
	}
}

//nolint:goconst
package executor

import (
	"strings"
	"testing"
)

func TestHandleClientSetInfoLibName(t *testing.T) {
	clients := NewClients()
	clients.Set(1, ClientInfo{ID: 1})

	got := handleClient(
		[][]byte{[]byte("SETINFO"), []byte("lib-name"), []byte("my-lib")},
		1, clients,
	)

	if string(got) != "+OK\r\n" {
		t.Fatalf("CLIENT SETINFO lib-name reply = %q, want %q", got, "+OK\r\n")
	}

	info := getClientInfo(t, clients, 1)
	if info.LibName != "my-lib" {
		t.Fatalf("LibName = %q, want %q", info.LibName, "my-lib")
	}
}

func TestHandleClientSetInfoLibVer(t *testing.T) {
	clients := NewClients()
	clients.Set(1, ClientInfo{ID: 1})

	got := handleClient(
		[][]byte{[]byte("SETINFO"), []byte("lib-ver"), []byte("2.0")},
		1, clients,
	)

	if string(got) != "+OK\r\n" {
		t.Fatalf("CLIENT SETINFO lib-ver reply = %q, want %q", got, "+OK\r\n")
	}

	info := getClientInfo(t, clients, 1)
	if info.LibVer != "2.0" {
		t.Fatalf("LibVer = %q, want %q", info.LibVer, "2.0")
	}
}

func TestHandleClientSetInfoIsCaseInsensitive(t *testing.T) {
	clients := NewClients()
	clients.Set(1, ClientInfo{ID: 1})

	got := handleClient(
		[][]byte{[]byte("SETINFO"), []byte("LIB-NAME"), []byte("my-lib")},
		1, clients,
	)

	if string(got) != "+OK\r\n" {
		t.Fatalf("CLIENT SETINFO LIB-NAME (uppercase) reply = %q, want %q", got, "+OK\r\n")
	}
	if info := getClientInfo(t, clients, 1); info.LibName != "my-lib" {
		t.Fatalf("LibName = %q, want %q", info.LibName, "my-lib")
	}
}

func TestHandleClientSetInfoUnknownAttribute(t *testing.T) {
	clients := NewClients()
	clients.Set(1, ClientInfo{ID: 1})

	got := handleClient(
		[][]byte{[]byte("SETINFO"), []byte("bogus-attr"), []byte("value")},
		1, clients,
	)

	s := string(got)
	if !strings.HasPrefix(s, "-ERR ") || !strings.Contains(s, "SETINFO") {
		t.Fatalf("CLIENT SETINFO unknown attribute = %q, want an error mentioning SETINFO", s)
	}

	// Confirm nothing was mutated on the unknown-attribute path.
	info := getClientInfo(t, clients, 1)
	if info.LibName != "" || info.LibVer != "" {
		t.Fatalf("client state mutated on unknown SETINFO attribute: %+v", info)
	}
}

func TestHandleClientSetName(t *testing.T) {
	clients := NewClients()
	clients.Set(1, ClientInfo{ID: 1})

	got := handleClient([][]byte{[]byte("SETNAME"), []byte("my-conn")}, 1, clients)

	if string(got) != "+OK\r\n" {
		t.Fatalf("CLIENT SETNAME reply = %q, want %q", got, "+OK\r\n")
	}
	if info := getClientInfo(t, clients, 1); info.Name != "my-conn" {
		t.Fatalf("Name = %q, want %q", info.Name, "my-conn")
	}
}

func TestHandleClientUnknownSubCommand(t *testing.T) {
	clients := NewClients()
	clients.Set(1, ClientInfo{ID: 1})

	got := handleClient([][]byte{[]byte("BOGUS")}, 1, clients)

	s := string(got)
	if !strings.HasPrefix(s, "-ERR ") {
		t.Fatalf("CLIENT BOGUS reply = %q, want an error reply with the ERR prefix", s)
	}
}

// getClientInfo reads back a client's info via Modify, since Clients has no
// exported Get. The identity function lets us observe state without
// mutating it.
func getClientInfo(t *testing.T, clients *Clients, id int64) ClientInfo {
	t.Helper()
	var info ClientInfo
	clients.Modify(id, func(current ClientInfo) ClientInfo {
		info = current
		return current
	})
	return info
}

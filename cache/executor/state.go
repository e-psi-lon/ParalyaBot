package executor

import "sync"

type ClientInfo struct {
	ID      int64
	LibName string
	LibVer  string
	Name    string
}
type Clients struct {
	mu      sync.RWMutex
	clients map[int64]ClientInfo
}

func NewClients() *Clients { return &Clients{clients: make(map[int64]ClientInfo)} }
func (c *Clients) Set(id int64, info ClientInfo) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.clients[id] = info
}
func (c *Clients) Modify(id int64, modifier func(ClientInfo) ClientInfo) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.clients[id] = modifier(c.clients[id])
}
func (c *Clients) Remove(id int64) { c.mu.Lock(); defer c.mu.Unlock(); delete(c.clients, id) }

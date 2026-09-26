package resp

const (
	eol              = "\r\n"
	respString  byte = '+'
	respError   byte = '-'
	respInteger byte = ':'
	respBulk    byte = '$'
	respArray   byte = '*'
)

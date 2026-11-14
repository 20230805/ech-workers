package tunnel

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

// ======================== 优选地址解析 ========================
// 优选栏可填 IP 或域名。填域名时先解析成 IP 再拨号，SNI/Host 仍用原服务域名。
// 解析顺序：系统 DNS -> UDP 223.5.5.5 / 119.29.29.29 -> DoH(阿里/腾讯/Cloudflare，按 IP 直连)。

var (
	prefResolveMu   sync.Mutex
	prefResolvedFor string
	prefResolvedIP  string
	prefResolvedAt  time.Time
)

// normalizePrefHost 去掉空格、协议头、路径和端口，只留主机名或 IP
func normalizePrefHost(s string) string {
	s = strings.TrimSpace(s)
	s = strings.TrimPrefix(strings.TrimPrefix(s, "https://"), "http://")
	if i := strings.IndexAny(s, "/?#"); i >= 0 {
		s = s[:i]
	}
	if h, _, err := net.SplitHostPort(s); err == nil {
		s = h
	}
	s = strings.TrimSuffix(strings.Trim(s, "[]"), ".")
	return strings.TrimSpace(s)
}

func pickPrefIP(ips []net.IP) string {
	for _, ip := range ips {
		if v4 := ip.To4(); v4 != nil {
			return v4.String()
		}
	}
	if len(ips) > 0 {
		return ips[0].String()
	}
	return ""
}

func prefLookupWith(host string, r *net.Resolver) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	addrs, err := r.LookupIPAddr(ctx, host)
	if err != nil {
		return "", err
	}
	ips := make([]net.IP, 0, len(addrs))
	for _, a := range addrs {
		ips = append(ips, a.IP)
	}
	if ip := pickPrefIP(ips); ip != "" {
		return ip, nil
	}
	return "", errors.New("没有返回任何 IP")
}

func prefUDPResolver(dnsAddr string) *net.Resolver {
	return &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
			d := net.Dialer{Timeout: 3 * time.Second}
			return d.DialContext(ctx, "udp", dnsAddr)
		},
	}
}

func prefBuildAQuery(host string) []byte {
	q := []byte{0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}
	for _, label := range strings.Split(host, ".") {
		if label == "" {
			continue
		}
		q = append(q, byte(len(label)))
		q = append(q, label...)
	}
	return append(q, 0x00, 0x00, 0x01, 0x00, 0x01)
}

func prefSkipName(b []byte, off int) int {
	for off < len(b) {
		l := int(b[off])
		if l == 0 {
			return off + 1
		}
		if l&0xC0 == 0xC0 {
			return off + 2
		}
		off += l + 1
	}
	return -1
}

func prefParseA(b []byte) (string, error) {
	if len(b) < 12 {
		return "", errors.New("DNS 响应过短")
	}
	if rcode := b[3] & 0x0F; rcode != 0 {
		return "", fmt.Errorf("DNS rcode=%d", rcode)
	}
	qd := int(binary.BigEndian.Uint16(b[4:6]))
	an := int(binary.BigEndian.Uint16(b[6:8]))
	off := 12
	for i := 0; i < qd; i++ {
		off = prefSkipName(b, off)
		if off < 0 || off+4 > len(b) {
			return "", errors.New("DNS 响应格式错误")
		}
		off += 4
	}
	for i := 0; i < an; i++ {
		off = prefSkipName(b, off)
		if off < 0 || off+10 > len(b) {
			return "", errors.New("DNS 响应格式错误")
		}
		typ := binary.BigEndian.Uint16(b[off : off+2])
		rdlen := int(binary.BigEndian.Uint16(b[off+8 : off+10]))
		off += 10
		if off+rdlen > len(b) {
			return "", errors.New("DNS 响应格式错误")
		}
		if typ == 1 && rdlen == 4 {
			return net.IP(b[off : off+4]).String(), nil
		}
		off += rdlen
	}
	return "", errors.New("没有 A 记录")
}

func prefDoHLookup(host, dohURL, dohIP, sni string) (string, error) {
	tr := &http.Transport{
		TLSClientConfig: &tls.Config{ServerName: sni},
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			d := net.Dialer{Timeout: 5 * time.Second}
			return d.DialContext(ctx, "tcp", net.JoinHostPort(dohIP, "443"))
		},
		Proxy: nil,
	}
	client := &http.Client{Transport: tr, Timeout: 8 * time.Second}
	req, err := http.NewRequest("POST", dohURL, bytes.NewReader(prefBuildAQuery(host)))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/dns-message")
	req.Header.Set("Accept", "application/dns-message")
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 65535))
	if err != nil {
		return "", err
	}
	return prefParseA(body)
}

// resolvePreferredIP 把优选栏内容变成可直接拨号的 IP（带 10 分钟缓存）
func resolvePreferredIP() (string, error) {
	host := normalizePrefHost(serverIP)
	if host == "" {
		return "", errors.New("优选地址为空")
	}
	if ip := net.ParseIP(host); ip != nil {
		return ip.String(), nil
	}

	prefResolveMu.Lock()
	defer prefResolveMu.Unlock()
	if prefResolvedFor == host && prefResolvedIP != "" && time.Since(prefResolvedAt) < 10*time.Minute {
		return prefResolvedIP, nil
	}

	type step struct {
		name string
		fn   func() (string, error)
	}
	steps := []step{
		{"系统DNS", func() (string, error) { return prefLookupWith(host, net.DefaultResolver) }},
		{"UDP 223.5.5.5", func() (string, error) { return prefLookupWith(host, prefUDPResolver("223.5.5.5:53")) }},
		{"UDP 119.29.29.29", func() (string, error) { return prefLookupWith(host, prefUDPResolver("119.29.29.29:53")) }},
		{"DoH dns.alidns.com(223.5.5.5)", func() (string, error) {
			return prefDoHLookup(host, "https://dns.alidns.com/dns-query", "223.5.5.5", "dns.alidns.com")
		}},
		{"DoH doh.pub(1.12.12.12)", func() (string, error) {
			return prefDoHLookup(host, "https://doh.pub/dns-query", "1.12.12.12", "doh.pub")
		}},
		{"DoH cloudflare-dns.com(1.1.1.1)", func() (string, error) {
			return prefDoHLookup(host, "https://cloudflare-dns.com/dns-query", "1.1.1.1", "cloudflare-dns.com")
		}},
	}

	var errs []string
	for _, s := range steps {
		ip, err := s.fn()
		if err == nil && ip != "" {
			log.Printf("[优选] %s -> %s (用 %s 解析)", host, ip, s.name)
			prefResolvedFor, prefResolvedIP, prefResolvedAt = host, ip, time.Now()
			return ip, nil
		}
		log.Printf("[优选] 用 %s 解析 %s 失败: %v", s.name, host, err)
		errs = append(errs, s.name+": "+fmt.Sprint(err))
	}

	// 全部失败时，若之前解析成功过就继续用旧结果
	if prefResolvedFor == host && prefResolvedIP != "" {
		log.Printf("[优选] 全部解析失败，继续使用上次结果 %s", prefResolvedIP)
		return prefResolvedIP, nil
	}
	return "", fmt.Errorf("优选域名 %s 解析失败 [%s]", host, strings.Join(errs, "; "))
}

// dialPreferred 用优选 IP 拨号，端口沿用原地址
func dialPreferred(ctx context.Context, network, address string) (net.Conn, error) {
	_, port, err := net.SplitHostPort(address)
	if err != nil {
		return nil, err
	}
	ip, err := resolvePreferredIP()
	if err != nil {
		return nil, err
	}
	d := net.Dialer{Timeout: 10 * time.Second}
	return d.DialContext(ctx, network, net.JoinHostPort(ip, port))
}

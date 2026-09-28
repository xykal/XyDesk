package main

import (
	"net/http"
	"testing"
)

func req(origin, host string) *http.Request {
	r, _ := http.NewRequest(http.MethodGet, "http://"+host+"/ws", nil)
	r.Host = host
	if origin != "" {
		r.Header.Set("Origin", origin)
	}
	return r
}

func TestOriginCheckerNativeClientsAlwaysPass(t *testing.T) {
	for _, allowed := range []string{"", "https://app.xydesk.my.id", "*"} {
		if !originChecker(allowed)(req("", "signal.local:8080")) {
			t.Fatalf("tanpa Origin harus lolos (allowed=%q)", allowed)
		}
	}
}

func TestOriginCheckerList(t *testing.T) {
	check := originChecker("https://app.xydesk.my.id, https://remote.xydesk.my.id")
	if !check(req("https://remote.xydesk.my.id", "signal.xydesk.my.id")) {
		t.Fatal("origin terdaftar ditolak")
	}
	if check(req("https://evil.example", "signal.xydesk.my.id")) {
		t.Fatal("origin asing lolos")
	}
}

func TestOriginCheckerEmptyListIsSameOriginOnly(t *testing.T) {
	check := originChecker("")
	if !check(req("http://192.168.1.5:8080", "192.168.1.5:8080")) {
		t.Fatal("same-origin LAN ditolak")
	}
	if check(req("http://evil.example", "192.168.1.5:8080")) {
		t.Fatal("lintas-origin lolos padahal daftar kosong")
	}
}

func TestOriginCheckerWildcard(t *testing.T) {
	if !originChecker("*")(req("https://apa.saja", "x")) {
		t.Fatal("wildcard harus lolos")
	}
}

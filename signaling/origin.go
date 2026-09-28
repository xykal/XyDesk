package main

import (
	"net/http"
	"strings"
)

// originChecker membangun CheckOrigin untuk upgrade WebSocket.
//
// Tanpa header Origin (host Windows, APK) selalu lolos. Dengan Origin:
//   - daftar kosong  → hanya same-origin (Host request == host Origin), pas
//     untuk cadangan LAN yang menyajikan web dari server yang sama;
//   - "*"            → semua;
//   - selain itu     → harus ada di daftar (dipisah koma).
func originChecker(allowed string) func(*http.Request) bool {
	list := map[string]bool{}
	for _, o := range strings.Split(allowed, ",") {
		if o = strings.TrimSpace(strings.ToLower(o)); o != "" {
			list[o] = true
		}
	}
	return func(r *http.Request) bool {
		origin := strings.ToLower(strings.TrimSpace(r.Header.Get("Origin")))
		if origin == "" {
			return true
		}
		if list["*"] || list[origin] {
			return true
		}
		if len(list) == 0 {
			host := origin
			if i := strings.Index(host, "://"); i >= 0 {
				host = host[i+3:]
			}
			return strings.EqualFold(host, r.Host)
		}
		return false
	}
}

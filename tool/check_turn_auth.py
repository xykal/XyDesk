#!/usr/bin/env python3
"""Periksa TURN sampai ke bukti: kredensialnya benar-benar bisa allocate.

Bedanya dengan `tool/check_turn_live.js`
----------------------------------------
`check_turn_live.js` hanya membuktikan **Worker menjawab** daftar `iceServers`.
Untuk penyedia `direct` (kredensial tetap dari env), jawaban itu selalu "ok"
selama tiga variabel terisi — tidak peduli kredensialnya masih sah atau sudah
mati di server TURN. Jadi gerbang lama bisa hijau sementara relay benar-benar
mati, dan yang menanggung akibatnya pengguna di balik CGNAT.

Skrip ini menutup celah itu: ia mengambil kredensial (atau memakai yang
diberikan) lalu **melakukan allocate TURN sungguhan** — STUN Allocate tanpa
autentikasi untuk mengambil realm+nonce, lalu Allocate ber-MESSAGE-INTEGRITY
dengan kredensial long-term. 200 (0x0103) = relay bisa dipakai. 401/403 =
kredensial ditolak server, apa pun kata Worker.

Pemakaian
---------
    # 1) lewat Worker (butuh ADMIN_SECRET; kredensial tidak pernah dicetak)
    ADMIN_SECRET=xxx python3 tool/check_turn_auth.py

    # 2) kredensial langsung (mis. untuk menguji satu penyedia saja)
    python3 tool/check_turn_auth.py \\
        --urls turn:free.expressturn.com:3478 --username 000000000000000000 \\
        --credential 'rahasia='

    # 3) uji diri sendiri tanpa jaringan (dipakai CI)
    python3 tool/check_turn_auth.py --self-test

Kode keluar: 0 semua relay menjawab allocate, 3 ada relay yang gagal,
2 masalah jaringan/HTTP, 4 kesalahan pemakaian.

Kredensial TIDAK pernah dicetak — hanya panjang dan potongan samar.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
import socket
import struct
import sys
import threading
import urllib.request

# ── Konstanta STUN/TURN (RFC 5389 / 5766) ────────────────────────────────
ALLOCATE, SUCCESS, ERROR = 0x0003, 0x0103, 0x0113
ATTR_USERNAME, ATTR_MESSAGE_INTEGRITY, ATTR_ERROR_CODE = 0x0006, 0x0008, 0x0009
ATTR_REALM, ATTR_NONCE, ATTR_REQ_TRANSPORT = 0x0014, 0x0015, 0x0019
ATTR_XOR_RELAYED = 0x0020
COOKIE = 0x2112A442
TRANSPORT_UDP = 17
TIMEOUT = 6.0


def attr(kind: int, value: bytes) -> bytes:
    pad = (4 - len(value) % 4) % 4
    return struct.pack("!HH", kind, len(value)) + value + b"\x00" * pad


def parse(data: bytes) -> tuple[int, dict[int, bytes], bytes]:
    if len(data) < 20:
        raise ValueError(f"balasan terlalu pendek: {len(data)} byte")
    kind, length, cookie = struct.unpack("!HHI", data[:8])
    if cookie != COOKIE:
        raise ValueError("bukan pesan STUN (magic cookie salah)")
    tid = data[8:20]
    out: dict[int, bytes] = {}
    i, end = 20, min(len(data), 20 + length)
    while i + 4 <= end:
        at, al = struct.unpack("!HH", data[i : i + 4])
        out.setdefault(at, data[i + 4 : i + 4 + al])
        i += 4 + al + ((4 - al % 4) % 4)
    return kind, out, tid


def error_text(value: bytes) -> str:
    if len(value) < 4:
        return "?"
    return f"{value[2] * 100 + value[3]} {value[4:].decode('utf-8', 'replace')}".strip()


def send_allocate(sock, server, attrs: bytes, key=None) -> tuple[int, dict[int, bytes]]:
    """Kirim Allocate. `attrs` = atribut yang sudah dikodekan (`attr(...)`).

    Bila `key` diberi, MESSAGE-INTEGRITY ditambahkan dan panjang di header
    disesuaikan lebih dulu — kesalahan klasik yang membuat server diam saja.
    """
    tid = os.urandom(12)
    body = attrs
    if key is not None:
        # Panjang pesan di header HARUS sudah termasuk atribut integrity.
        head = struct.pack("!HHI", ALLOCATE, len(body) + 24, COOKIE) + tid + body
        body += attr(ATTR_MESSAGE_INTEGRITY, hmac.new(key, head, hashlib.sha1).digest())
    sock.sendto(struct.pack("!HHI", ALLOCATE, len(body), COOKIE) + tid + body, server)
    data, _ = sock.recvfrom(2048)
    kind, parsed, _ = parse(data)
    return kind, parsed


def long_term_key(username: str, realm: str, credential: str) -> bytes:
    return hashlib.md5(f"{username}:{realm}:{credential}".encode()).digest()


def probe_udp(host: str, port: int, username: str, credential: str) -> dict:
    """Allocate sungguhan ke satu server TURN UDP."""
    result: dict = {"server": f"{host}:{port}"}
    try:
        server = (socket.gethostbyname(host), port)
    except OSError as exc:
        return {**result, "ok": False, "why": f"DNS gagal: {exc}"}

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(TIMEOUT)
    try:
        transport = attr(ATTR_REQ_TRANSPORT, struct.pack("!BBBB", TRANSPORT_UDP, 0, 0, 0))
        kind, parsed = send_allocate(sock, server, transport)
        if ATTR_REALM not in parsed:
            return {
                **result,
                "ok": False,
                "why": f"server tidak menantang autentikasi (tipe 0x{kind:04x}) — "
                "bukan TURN ber-kredensial?",
            }
        realm = parsed[ATTR_REALM].decode("utf-8", "replace")
        result["realm"] = realm
        if ATTR_NONCE not in parsed:
            return {**result, "ok": False, "why": "tantangan tanpa NONCE"}

        key = long_term_key(username, realm, credential)
        kind, parsed = send_allocate(
            sock,
            server,
            transport
            + attr(ATTR_USERNAME, username.encode())
            + attr(ATTR_REALM, parsed[ATTR_REALM])
            + attr(ATTR_NONCE, parsed[ATTR_NONCE]),
            key=key,
        )
        if kind == SUCCESS:
            relayed = parsed.get(ATTR_XOR_RELAYED)
            if relayed and len(relayed) >= 8:
                port_x = struct.unpack("!H", relayed[2:4])[0] ^ (COOKIE >> 16)
                raw_ip = bytes(b ^ c for b, c in zip(relayed[4:8], struct.pack("!I", COOKIE)))
                result["relayed"] = f"{socket.inet_ntoa(raw_ip)}:{port_x}"
            return {**result, "ok": True}
        why = error_text(parsed.get(ATTR_ERROR_CODE, b"")) if ATTR_ERROR_CODE in parsed else "?"
        return {
            **result,
            "ok": False,
            "why": f"allocate ditolak: {why} (kredensial TIDAK diterima server TURN)",
        }
    except socket.timeout:
        return {**result, "ok": False, "why": f"tidak ada balasan dalam {TIMEOUT:.0f} detik (UDP)"}
    except Exception as exc:  # noqa: BLE001 — apa pun, laporkan apa adanya
        return {**result, "ok": False, "why": f"{type(exc).__name__}: {exc}"}
    finally:
        sock.close()


def mask(secret: str) -> str:
    if not secret:
        return "(kosong)"
    return f"<{len(secret)} karakter, {secret[:2]}…>"


def split_url(url: str) -> tuple[str, str, int] | None:
    """(skema, host, port) untuk URL turn/turns; None bila bukan TURN UDP."""
    if url.startswith("turn:"):
        scheme, rest = "turn", url[len("turn:") :]
    elif url.startswith("turns:"):
        scheme, rest = "turns", url[len("turns:") :]
    else:
        return None
    rest = rest.split("?")[0]  # buang ?transport=...
    rest = rest.rsplit("@", 1)[-1]  # buang userinfo
    host, _, port = rest.rpartition(":")
    if not host:
        return None
    return scheme, host, int(port or 3478)


def collect_via_worker(signal: str, admin: str, role: str) -> tuple[list[dict], dict]:
    url = f"{signal.rstrip('/')}/turn-ice?id=check-turn-auth&role={role}"
    # User-Agent wajib eksplisit: penyaring bot Cloudflare di zona xydesk.my.id
    # menolak User-Agent bawaan urllib dengan error 1010 (403), yang di CI
    # terbaca keliru sebagai "kredensial TURN ditolak".
    request = urllib.request.Request(
        url,
        headers={
            "X-Admin": admin,
            "User-Agent": "XyDesk-check-turn-auth/1.0 (CI gate; +https://github.com/xykal/XyDesk)",
        },
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        payload = json.load(response)
    return payload.get("iceServers", []), payload.get("providers", {})


def run(servers: list[dict], expected_user: str | None, expected_cred: str | None) -> int:
    checked = failed = skipped = 0
    for entry in servers:
        urls = entry.get("urls") if isinstance(entry.get("urls"), list) else [entry.get("urls")]
        username = expected_user if expected_user is not None else str(entry.get("username", ""))
        credential = expected_cred if expected_cred is not None else str(entry.get("credential", ""))
        for url in urls:
            if not url:
                continue
            parsed = split_url(str(url))
            where = str(url).split("?")[0]
            if parsed is None:
                print(f"  – lewati {where} (bukan URL TURN UDP)")
                skipped += 1
                continue
            scheme, host, port = parsed
            if scheme == "turns":
                # turns: butuh TLS; biarkan jujur daripada memberi hasil palsu.
                print(f"  – lewati {where} (TLS, belum diperiksa skrip ini)")
                skipped += 1
                continue
            print(f"  → allocate {where} sebagai {username[:6]}… (kredensial {mask(credential)})")
            hasil = probe_udp(host, port, username, credential)
            checked += 1
            if hasil["ok"]:
                relay = hasil.get("relayed", "")
                print(f"    OK — relay {relay}, realm {hasil.get('realm')!r}")
            else:
                failed += 1
                print(f"    GAGAL — {hasil['why']}")
    print(f"Ringkas: {checked} diperiksa, {failed} gagal, {skipped} dilewati")
    return 0 if (checked and not failed) else 3


# ── Uji diri: server TURN tiruan di loopback ─────────────────────────────
# Menjaga logika klien (tantangan → integrity → 200) tanpa jaringan luar,
# sekaligus membuktikan skrip ini GAGAL saat kredensialnya salah.
class FakeTurn(threading.Thread):
    def __init__(self, username: str, credential: str, realm: str = "uji.local"):
        super().__init__(daemon=True)
        self.username, self.credential, self.realm = username, credential, realm
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(("127.0.0.1", 0))
        self.port = self.sock.getsockname()[1]
        self.nonce = base64.b64encode(os.urandom(12)).decode()

    def run(self) -> None:
        while True:
            try:
                data, addr = self.sock.recvfrom(2048)
            except OSError:
                return
            try:
                kind, parsed, tid = parse(data)
            except ValueError:
                continue
            if kind != ALLOCATE or addr is None:
                continue
            if ATTR_MESSAGE_INTEGRITY not in parsed:
                self.sock.sendto(
                    self._error(tid, 401, "Unauthorized"), addr
                )
                continue
            # Hitung ulang integrity atas pesan sampai atribut itu (panjang
            # header disesuaikan) — persis yang dilakukan server TURN asli.
            i, end = 20, 20 + struct.unpack("!H", data[2:4])[0]
            while i + 4 <= end:
                at, al = struct.unpack("!HH", data[i : i + 4])
                if at == ATTR_MESSAGE_INTEGRITY:
                    head = data[:2] + struct.pack("!H", i - 20 + 24) + data[4:i]
                    key = long_term_key(self.username, self.realm, self.credential)
                    expect = hmac.new(key, head, hashlib.sha1).digest()
                    if hmac.compare_digest(expect, parsed[at]):
                        self.sock.sendto(self._success(tid), addr)
                    else:
                        self.sock.sendto(self._error(tid, 401, "Unauthorized"), addr)
                    break
                i += 4 + al + ((4 - al % 4) % 4)

    def _error(self, tid: bytes, code: int, reason: str) -> bytes:
        body = attr(ATTR_ERROR_CODE, struct.pack("!BBBB", 0, 0, code // 100, code % 100) + reason.encode())
        if code == 401:
            body += attr(ATTR_REALM, self.realm.encode()) + attr(ATTR_NONCE, self.nonce.encode())
        return struct.pack("!HHI", ERROR, len(body), COOKIE) + tid + body

    def _success(self, tid: bytes) -> bytes:
        ip = socket.inet_aton("127.0.0.1")
        xored = bytes(b ^ c for b, c in zip(ip, struct.pack("!I", COOKIE)))
        body = attr(ATTR_XOR_RELAYED, b"\x00\x01" + struct.pack("!H", 40000 ^ (COOKIE >> 16)) + xored)
        return struct.pack("!HHI", SUCCESS, len(body), COOKIE) + tid + body


def self_test() -> int:
    print("Uji diri: server TURN tiruan di loopback")
    server = FakeTurn("pengguna-uji", "kredensial-uji")
    server.start()

    benar = probe_udp("127.0.0.1", server.port, "pengguna-uji", "kredensial-uji")
    assert benar["ok"], f"kredensial benar harus LULUS, dapat: {benar}"
    print(f"  OK  kredensial benar -> {benar.get('relayed')} (realm {benar['realm']!r})")

    salah = probe_udp("127.0.0.1", server.port, "pengguna-uji", "kredensial-salah")
    assert not salah["ok"], "kredensial salah harus GAGAL"
    print(f"  OK  kredensial salah -> ditolak: {salah['why']}")

    salah_user = probe_udp("127.0.0.1", server.port, "pengguna-lain", "kredensial-uji")
    assert not salah_user["ok"], "username salah harus GAGAL"
    print(f"  OK  username salah -> ditolak: {salah_user['why']}")

    assert split_url("turn:free.expressturn.com:3478?transport=tcp") == (
        "turn",
        "free.expressturn.com",
        3478,
    )
    assert split_url("turn:user:pass@relay.example:3478") == ("turn", "relay.example", 3478)
    assert split_url("stun:stun.l.google.com:19302") is None
    print("  OK  parsing URL (userinfo & query dibuang, STUN dilewati)")
    print("Uji diri lulus.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Buktikan kredensial TURN bisa allocate.")
    parser.add_argument("--signal", default=os.environ.get("SIGNAL_URL", "https://signal.xydesk.my.id"))
    parser.add_argument("--admin", default=os.environ.get("ADMIN_SECRET", ""))
    parser.add_argument("--role", default="host", choices=["host", "client"])
    parser.add_argument("--urls", help="daftar URL dipisah koma (tanpa Worker)")
    parser.add_argument("--username")
    parser.add_argument("--credential")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    if args.urls:
        if not args.username or not args.credential:
            parser.error("--urls butuh --username dan --credential")
        entries = [{"urls": [u.strip() for u in args.urls.split(",") if u.strip()],
                    "username": args.username, "credential": args.credential}]
    elif args.admin:
        print(f"→ GET {args.signal}/turn-ice (role={args.role})")
        try:
            entries, providers = collect_via_worker(args.signal, args.admin, args.role)
        except Exception as exc:  # noqa: BLE001
            print(f"✗ gagal mengambil kredensial: {type(exc).__name__}: {exc}")
            return 2
        print(f"  penyedia: {json.dumps(providers, ensure_ascii=False)}")
        if not entries:
            print("✗ Worker tidak mengirim satu pun iceServers — TURN belum dikonfigurasi?")
            return 3
    else:
        parser.error("butuh --admin (atau ADMIN_SECRET) atau --urls/--username/--credential")

    return run(entries, args.username, args.credential)


if __name__ == "__main__":
    sys.exit(main())

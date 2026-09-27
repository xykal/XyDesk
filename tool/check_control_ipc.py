#!/usr/bin/env python3
"""Windows/Python >=3.12: probe pipe bootstrap + auth tanpa jaringan eksternal.

Menjalankan engine kandidat pada profil sementara. Bukan launcher produksi.
Jangan mencetak payload IPC atau respons /status: keduanya memuat secret.
"""
import argparse
import http.client
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from urllib.parse import urlsplit


def probe(engine):
    if sys.platform != "win32" or sys.version_info < (3, 12):
        raise RuntimeError("probe requires Windows and Python >=3.12")
    import msvcrt

    with tempfile.TemporaryDirectory(prefix="xydesk-private-ipc-") as home:
        read_fd, write_fd = os.pipe()
        process = None
        try:
            os.set_blocking(read_fd, False)
            handle = msvcrt.get_osfhandle(write_fd)
            os.set_handle_inheritable(handle, True)
            startup = subprocess.STARTUPINFO()
            # Daftar eksplisit: jangan wariskan semua handle proses launcher.
            startup.lpAttributeList = {"handle_list": [handle]}
            process = subprocess.Popen(
                [engine, "--control-info-handle", str(handle),
                 "--url", "ws://127.0.0.1:9/ws", "--token", "local-validation-only"],
                env={**os.environ, "XYDESK_HOME": home},
                stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                close_fds=True, startupinfo=startup, creationflags=subprocess.CREATE_NO_WINDOW,
            )
            os.set_handle_inheritable(handle, False)
            os.close(write_fd)
            write_fd = None
            deadline = time.monotonic() + 10
            data = bytearray()
            while b"\n" not in data:
                if time.monotonic() >= deadline or process.poll() is not None:
                    raise RuntimeError("engine failed or IPC deadline exceeded")
                try:
                    chunk = os.read(read_fd, 1025 - len(data))
                except BlockingIOError:
                    time.sleep(0.01)
                    continue
                if not chunk:
                    raise RuntimeError("incomplete IPC frame")
                data.extend(chunk)
                if len(data) > 1024:
                    raise RuntimeError("oversized IPC frame")
            metadata = json.loads(data)
            endpoint = urlsplit(metadata.get("url", ""))
            token = metadata.get("token", "")
            if (metadata.get("protocol") != 1 or metadata.get("pid") != process.pid
                    or endpoint.scheme != "http" or endpoint.hostname != "127.0.0.1"
                    or endpoint.username is not None or endpoint.password is not None
                    or endpoint.path or endpoint.query or endpoint.fragment
                    or not endpoint.port or not isinstance(token, str)
                    or not re.fullmatch(r"[0-9a-f]{32}", token)):
                raise RuntimeError("invalid IPC contract")
            # Direct loopback HTTP: tidak memakai proxy atau mengikuti redirect.
            for credential, expected in [("wrong-fixture", 401), (token, 200)]:
                connection = http.client.HTTPConnection("127.0.0.1", endpoint.port, timeout=10)
                try:
                    connection.request("GET", "/status", headers={"x-xydesk-token": credential})
                    response = connection.getresponse()
                    if response.status != expected:
                        raise RuntimeError("control authorization result mismatch")
                    # Jangan baca/cetak body /status ke log CI.
                finally:
                    connection.close()
            print("Private control IPC: PID/endpoint valid; invalid bearer rejected; inherited bearer accepted.")
        finally:
            if write_fd is not None:
                os.close(write_fd)
            os.close(read_fd)
            if process is not None:
                if process.poll() is None:
                    process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--engine", required=True)
    args = parser.parse_args()
    try:
        probe(os.path.abspath(args.engine))
    except Exception:
        # Exception pihak ketiga dapat mengandung potongan payload; jangan log.
        print("Private control IPC probe failed; no credential payload logged.", file=sys.stderr)
        sys.exit(1)

#!/usr/bin/env python3
"""Local Qingsong callback and file server for JetFileII integration tests."""

import argparse
import hashlib
import json
import os
import socket
import sys
import tempfile
import threading
import unittest
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


FILE_NAME = "qingsong-mock.bmp"
DEFAULT_SCREEN_IP = "192.168.113.88"
DEFAULT_SCREEN_PORT = 9520
DEFAULT_VENDOR_HINT = "JETFILEII"


def _le16(value):
    return int(value).to_bytes(2, byteorder="little")


def _le32(value):
    return int(value).to_bytes(4, byteorder="little")


def _build_bmp():
    width = 64
    height = 64
    row_size = ((width * 3 + 3) // 4) * 4
    pixel_size = row_size * height
    file_size = 54 + pixel_size
    header = b"BM"
    header += _le32(file_size)
    header += b"\x00\x00\x00\x00"
    header += _le32(54)
    header += _le32(40)
    header += _le32(width)
    header += _le32(height)
    header += _le16(1)
    header += _le16(24)
    header += _le32(0)
    header += _le32(pixel_size)
    header += _le32(2835)
    header += _le32(2835)
    header += _le32(0)
    header += _le32(0)
    rows = []
    for y in range(height):
        row = bytearray()
        for x in range(width):
            if (x // 8 + y // 8) % 2 == 0:
                row.extend((0x00, 0x66, 0xff))
            else:
                row.extend((0xff, 0xff, 0xff))
        row.extend(b"\x00" * (row_size - width * 3))
        rows.append(bytes(row))
    return header + b"".join(reversed(rows))


BMP_BYTES = _build_bmp()
BMP_SHA256 = hashlib.sha256(BMP_BYTES).hexdigest()


def load_file_bytes(file_path):
    if not file_path:
        return BMP_BYTES, BMP_SHA256
    with open(file_path, "rb") as fp:
        file_bytes = fp.read()
    return file_bytes, hashlib.sha256(file_bytes).hexdigest()


def detect_host_ip():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def build_program(ip, host_ip, port, screen_port,
                  vendor_hint=DEFAULT_VENDOR_HINT, file_name=FILE_NAME, file_hash=BMP_SHA256):
    target_ip = ip or DEFAULT_SCREEN_IP
    playlist_id = "QS-MOCK-" + target_ip.replace(".", "-")
    file_url = "http://{}:{}/files/{}".format(host_ip, port, file_name)
    return {
        "code": 0,
        "message": "success",
        "data": {
            "success": True,
            "playlistId": playlist_id,
            "target": {
                "deviceId": "MOCK-" + target_ip.replace(".", "-"),
                "ip": target_ip,
                "port": screen_port,
                "vendorHint": vendor_hint,
            },
            "items": [
                {
                    "orderNo": 1,
                    "fileName": file_name,
                    "fileType": "image",
                    "fileUrl": file_url,
                    "durationSeconds": 5,
                    "fileHash": file_hash,
                }
            ],
        },
    }


class MockFileManagerHandler(BaseHTTPRequestHandler):

    server_version = "QingsongMockFileManager/1.0"

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self._send_json({
                "code": 0,
                "message": "ok",
                "data": {
                    "service": "qingsong-mock-file-manager",
                    "fileName": self.server.file_name,
                    "fileSize": len(self.server.file_bytes),
                    "fileHash": self.server.file_hash,
                },
            })
            return

        if parsed.path == "/api/v1/callback/program-by-ip":
            params = parse_qs(parsed.query)
            ip = first(params.get("ip")) or self.server.screen_ip
            self._send_json(build_program(ip, self.server.host_ip, self.server.server_port,
                                          self.server.screen_port, self.server.vendor_hint,
                                          self.server.file_name, self.server.file_hash))
            return

        if parsed.path == "/files/" + self.server.file_name:
            self._send_bytes(self.server.file_bytes, "image/bmp")
            return

        self._send_json({"code": 404, "message": "not found", "data": None}, status=404)

    def log_message(self, fmt, *args):
        if not getattr(self.server, "quiet", False):
            super(MockFileManagerHandler, self).log_message(fmt, *args)

    def _send_json(self, payload, status=200):
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_bytes(self, body, content_type, status=200):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def first(values):
    if not values:
        return None
    for value in values:
        if value:
            return value
    return None


class MockFileManagerSelfTest(unittest.TestCase):

    def test_bmp_hash_is_stable(self):
        self.assertEqual(12342, len(BMP_BYTES))
        self.assertEqual(hashlib.sha256(BMP_BYTES).hexdigest(), BMP_SHA256)

    def test_bmp_is_64_by_64(self):
        self.assertEqual(64, int.from_bytes(BMP_BYTES[18:22], byteorder="little"))
        self.assertEqual(64, int.from_bytes(BMP_BYTES[22:26], byteorder="little"))

    def test_program_uses_requested_target_ip(self):
        program = build_program("192.168.113.88", "192.168.1.168", 8199, 9520)
        data = program["data"]
        self.assertTrue(data["success"])
        self.assertEqual("192.168.113.88", data["target"]["ip"])
        self.assertEqual(9520, data["target"]["port"])
        self.assertEqual("JETFILEII", data["target"]["vendorHint"])
        self.assertEqual(BMP_SHA256, data["items"][0]["fileHash"])
        self.assertEqual("http://192.168.1.168:8199/files/qingsong-mock.bmp", data["items"][0]["fileUrl"])

    def test_program_can_target_colorlight(self):
        program = build_program("192.168.113.181", "192.168.1.168", 8199, 8989,
                                "COLORLIGHT", "colorlight-test.bmp", BMP_SHA256)
        data = program["data"]
        self.assertEqual("192.168.113.181", data["target"]["ip"])
        self.assertEqual(8989, data["target"]["port"])
        self.assertEqual("COLORLIGHT", data["target"]["vendorHint"])
        self.assertEqual("colorlight-test.bmp", data["items"][0]["fileName"])
        self.assertEqual("http://192.168.1.168:8199/files/colorlight-test.bmp",
                         data["items"][0]["fileUrl"])

    def test_http_endpoints(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), MockFileManagerHandler)
        server.host_ip = "127.0.0.1"
        server.screen_ip = DEFAULT_SCREEN_IP
        server.screen_port = DEFAULT_SCREEN_PORT
        server.vendor_hint = DEFAULT_VENDOR_HINT
        server.file_name = FILE_NAME
        server.file_bytes = BMP_BYTES
        server.file_hash = BMP_SHA256
        server.quiet = True
        thread = threading.Thread(target=server.serve_forever)
        thread.daemon = True
        thread.start()
        try:
            base_url = "http://127.0.0.1:{}".format(server.server_port)
            health = json.loads(urllib.request.urlopen(base_url + "/health", timeout=5).read().decode("utf-8"))
            self.assertEqual(0, health["code"])
            self.assertEqual(BMP_SHA256, health["data"]["fileHash"])

            url = base_url + "/api/v1/callback/program-by-ip?ip=192.168.113.88"
            program = json.loads(urllib.request.urlopen(url, timeout=5).read().decode("utf-8"))
            self.assertEqual("192.168.113.88", program["data"]["target"]["ip"])
            self.assertEqual(BMP_SHA256, program["data"]["items"][0]["fileHash"])

            content = urllib.request.urlopen(base_url + "/files/" + FILE_NAME, timeout=5).read()
            self.assertEqual(BMP_BYTES, content)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(5)

    def test_http_endpoints_use_server_colorlight_settings(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), MockFileManagerHandler)
        server.host_ip = "127.0.0.1"
        server.screen_ip = "192.168.113.181"
        server.screen_port = 8989
        server.vendor_hint = "COLORLIGHT"
        server.file_name = "colorlight-test.bmp"
        server.file_bytes = BMP_BYTES
        server.file_hash = BMP_SHA256
        server.quiet = True
        thread = threading.Thread(target=server.serve_forever)
        thread.daemon = True
        thread.start()
        try:
            base_url = "http://127.0.0.1:{}".format(server.server_port)
            url = base_url + "/api/v1/callback/program-by-ip?ip=192.168.113.181"
            program = json.loads(urllib.request.urlopen(url, timeout=5).read().decode("utf-8"))
            data = program["data"]
            self.assertEqual("192.168.113.181", data["target"]["ip"])
            self.assertEqual(8989, data["target"]["port"])
            self.assertEqual("COLORLIGHT", data["target"]["vendorHint"])
            self.assertEqual("colorlight-test.bmp", data["items"][0]["fileName"])
            self.assertEqual(BMP_SHA256, data["items"][0]["fileHash"])

            content = urllib.request.urlopen(base_url + "/files/colorlight-test.bmp", timeout=5).read()
            self.assertEqual(BMP_BYTES, content)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(5)

    def test_cli_accepts_colorlight_settings(self):
        args = parse_args([
            "--screen-ip", "192.168.113.181",
            "--screen-port", "8989",
            "--vendor-hint", "COLORLIGHT",
            "--file-name", "colorlight-test.bmp",
        ])
        self.assertEqual("192.168.113.181", args.screen_ip)
        self.assertEqual(8989, args.screen_port)
        self.assertEqual("COLORLIGHT", args.vendor_hint)
        self.assertEqual("colorlight-test.bmp", args.file_name)

    def test_file_path_payload_uses_disk_file_hash(self):
        with tempfile.NamedTemporaryFile(delete=False) as tmp:
            tmp.write(b"BMexternal-file")
            tmp.flush()
            tmp_path = tmp.name
        try:
            file_bytes, file_hash = load_file_bytes(tmp_path)
            self.assertEqual(b"BMexternal-file", file_bytes)
            self.assertEqual(hashlib.sha256(b"BMexternal-file").hexdigest(), file_hash)
        finally:
            os.remove(tmp_path)

    def test_cli_accepts_file_path(self):
        args = parse_args([
            "--file-path", r"D:\tmp\45.BMP",
        ])
        self.assertEqual(r"D:\tmp\45.BMP", args.file_path)


def run_self_test():
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(MockFileManagerSelfTest)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    return 0 if result.wasSuccessful() else 1


def serve(args):
    host_ip = args.host_ip or detect_host_ip()
    file_bytes, file_hash = load_file_bytes(args.file_path)
    server = ThreadingHTTPServer((args.bind, args.port), MockFileManagerHandler)
    server.host_ip = host_ip
    server.screen_ip = args.screen_ip
    server.screen_port = args.screen_port
    server.vendor_hint = args.vendor_hint
    server.file_name = args.file_name
    server.file_bytes = file_bytes
    server.file_hash = file_hash
    server.quiet = args.quiet
    print("Qingsong mock file manager listening on {}:{}".format(args.bind, server.server_port))
    print("sigmaBaseUrl: http://{}:{}".format(host_ip, server.server_port))
    print("program: http://{}:{}/api/v1/callback/program-by-ip?ip={}".format(
        host_ip, server.server_port, args.screen_ip))
    print("target: {}:{} {}".format(args.screen_ip, args.screen_port, args.vendor_hint))
    print("fileName: {}".format(args.file_name))
    print("fileHash: {}".format(file_hash))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nshutting down")
    finally:
        server.server_close()


def parse_args(argv):
    parser = argparse.ArgumentParser(description="Qingsong local mock file manager")
    parser.add_argument("--bind", default="0.0.0.0", help="listen address, default: 0.0.0.0")
    parser.add_argument("--port", type=int, default=8199, help="listen port, default: 8199")
    parser.add_argument("--host-ip", default=None, help="host IP used in returned fileUrl")
    parser.add_argument("--screen-ip", default=DEFAULT_SCREEN_IP, help="default target screen IP")
    parser.add_argument("--screen-port", type=int, default=DEFAULT_SCREEN_PORT, help="target screen port")
    parser.add_argument("--vendor-hint", default=DEFAULT_VENDOR_HINT,
                        help="target vendor hint, default: JETFILEII")
    parser.add_argument("--file-name", default=FILE_NAME,
                        help="file name exposed under /files, default: qingsong-mock.bmp")
    parser.add_argument("--file-path", default=None,
                        help="optional local file to serve instead of the built-in 64x64 BMP")
    parser.add_argument("--quiet", action="store_true", help="disable access logs")
    parser.add_argument("--self-test", action="store_true", help="run built-in tests and exit")
    return parser.parse_args(argv)


def main(argv):
    args = parse_args(argv)
    if args.self_test:
        return run_self_test()
    serve(args)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

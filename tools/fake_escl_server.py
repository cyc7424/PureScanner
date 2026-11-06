#!/usr/bin/env python3
"""Tiny fake eSCL scanner for developing without a printer.

    python3 tools/fake_escl_server.py [--port 8080] [--image path/to/page.jpg] [--delay 3]

From the Android emulator, add the device manually with IP 10.0.2.2 and port 8080.
The server returns the given JPEG for every scan (default: a design screenshot).
"""
import argparse
import itertools
import pathlib
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CAPS = """<?xml version="1.0" encoding="UTF-8"?>
<scan:ScannerCapabilities xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03"
                          xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm">
  <pwg:Version>2.63</pwg:Version>
  <pwg:MakeAndModel>Fake eSCL Scanner</pwg:MakeAndModel>
  <scan:UUID>{uuid}</scan:UUID>
  <scan:Platen><scan:PlatenInputCaps>
    <scan:MinWidth>16</scan:MinWidth><scan:MaxWidth>2550</scan:MaxWidth>
    <scan:MinHeight>16</scan:MinHeight><scan:MaxHeight>3507</scan:MaxHeight>
    <scan:SettingProfiles><scan:SettingProfile>
      <scan:ColorModes><scan:ColorMode>Grayscale8</scan:ColorMode><scan:ColorMode>RGB24</scan:ColorMode></scan:ColorModes>
      <scan:DocumentFormats><pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat></scan:DocumentFormats>
      <scan:SupportedResolutions><scan:DiscreteResolutions>
        <scan:DiscreteResolution><scan:XResolution>150</scan:XResolution><scan:YResolution>150</scan:YResolution></scan:DiscreteResolution>
        <scan:DiscreteResolution><scan:XResolution>300</scan:XResolution><scan:YResolution>300</scan:YResolution></scan:DiscreteResolution>
        <scan:DiscreteResolution><scan:XResolution>600</scan:XResolution><scan:YResolution>600</scan:YResolution></scan:DiscreteResolution>
      </scan:DiscreteResolutions></scan:SupportedResolutions>
    </scan:SettingProfile></scan:SettingProfiles>
  </scan:PlatenInputCaps></scan:Platen>
</scan:ScannerCapabilities>
"""

STATUS = """<?xml version="1.0" encoding="UTF-8"?>
<scan:ScannerStatus xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03"
                    xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm">
  <pwg:Version>2.63</pwg:Version><pwg:State>{state}</pwg:State>
</scan:ScannerStatus>
"""

args = None
image = b""
jobs = {}  # job id -> pages still to deliver
counter = itertools.count(1)


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body=b"", ctype="text/xml", headers=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/eSCL/ScannerCapabilities":
            self._send(200, CAPS.format(uuid=SERVER_UUID).encode())
        elif self.path == "/eSCL/ScannerStatus":
            self._send(200, STATUS.format(state="Processing" if jobs else "Idle").encode())
        elif self.path.startswith("/eSCL/ScanJobs/") and self.path.endswith("/NextDocument"):
            job = self.path.split("/")[3]
            if job not in jobs or jobs[job] == 0:
                self._send(404)
                return
            time.sleep(args.delay)  # pretend the carriage is moving
            jobs[job] -= 1
            self._send(200, image, "image/jpeg")
        else:
            self._send(404)

    def do_POST(self):
        if self.path == "/eSCL/ScanJobs":
            self.rfile.read(int(self.headers.get("Content-Length", 0)))
            job = f"job{next(counter)}"
            jobs[job] = 1  # platen: one page per job
            self._send(201, headers={"Location": f"/eSCL/ScanJobs/{job}"})
        else:
            self._send(404)

    def do_DELETE(self):
        jobs.pop(self.path.rsplit("/", 1)[-1], None)
        self._send(200)

    def log_message(self, fmt, *a):
        print(self.address_string(), fmt % a)


if __name__ == "__main__":
    root = pathlib.Path(__file__).resolve().parent.parent
    default_image = root / "app_design_screenshot" / "device select.jpg"
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=8080)
    p.add_argument("--image", type=pathlib.Path, default=default_image)
    p.add_argument("--delay", type=float, default=3.0, help="seconds each page takes to scan")
    args = p.parse_args()
    image = args.image.read_bytes()
    SERVER_UUID = str(uuid.uuid4())
    print(f"Fake eSCL scanner on 0.0.0.0:{args.port}, serving {args.image}")
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()

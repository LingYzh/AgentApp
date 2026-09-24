"""Small emulator-only UI helper; target bounds always come from a fresh UI tree."""
import argparse
import pathlib
import subprocess
import xml.etree.ElementTree as ET
import re
import sys
import time
import os

sys.stdout.reconfigure(encoding="utf-8")

_properties = pathlib.Path(__file__).resolve().parents[2] / "local.properties"
_sdk_line = next((line.split("=", 1)[1] for line in _properties.read_text(encoding="utf-8").splitlines()
                  if line.startswith("sdk.dir=")), "")
_sdk = _sdk_line.replace("\\:", ":").replace("\\\\", "\\") or os.environ.get("ANDROID_HOME", "")
ADB = str(pathlib.Path(_sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))


def adb(serial, *args):
    return subprocess.run([ADB, "-s", serial, *args], check=True, capture_output=True).stdout


def tree(serial):
    for _ in range(3):
        output = adb(serial, "shell", "uiautomator", "dump", "/sdcard/agentapp-ui.xml")
        if b"dumped to" in output:
            return ET.fromstring(adb(serial, "exec-out", "cat", "/sdcard/agentapp-ui.xml"))
        time.sleep(1)
    raise RuntimeError("No fresh UI tree; refusing to use the previous dump.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["inspect", "tap", "screenshot"])
    parser.add_argument("target", nargs="?")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        raise SystemExit("This helper only operates emulators.")
    if args.action == "screenshot":
        pathlib.Path(args.target).write_bytes(adb(args.serial, "exec-out", "screencap", "-p"))
        return
    root = tree(args.serial)
    if args.action == "inspect":
        for node in root.iter("node"):
            label = node.get("text") or node.get("content-desc")
            if node.get("password") == "true":
                label = "[password hidden]"
            if not label and node.get("checkable") == "true":
                label = "@switch (checked=" + node.get("checked", "") + ")"
            if label:
                print(label, node.get("bounds"))
        return
    matches = [n for n in root.iter("node") if args.target in (n.get("text"), n.get("content-desc"))
               or (args.target == "@switch" and n.get("checkable") == "true")]
    if len(matches) != 1:
        raise SystemExit(f"Expected one target, found {len(matches)}")
    left, top, right, bottom = map(int, re.findall(r"\d+", matches[0].get("bounds")))
    adb(args.serial, "shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


if __name__ == "__main__":
    main()

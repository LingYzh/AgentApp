"""Prepare/run device-control QA on emulator-5554 only. Never selects a real phone."""
import argparse
import importlib.util
import pathlib
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")
ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("emu", ROOT / ".Codex/tools/emulator_ui.py")
emu = importlib.util.module_from_spec(spec)
spec.loader.exec_module(emu)
SERIAL = "emulator-5554"
OUT = ROOT / "build/device-control-qa"
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, timeout=120):
    result = subprocess.run([emu.ADB, "-s", SERIAL, *args], capture_output=True, timeout=timeout)
    text = result.stdout.decode("utf-8", errors="replace")
    if result.returncode:
        raise RuntimeError(text + result.stderr.decode("utf-8", errors="replace"))
    return text


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["prepare", "tests", "logs", "launch", "diagnostics"])
    parser.add_argument("--method", default="")
    args = parser.parse_args()
    hardware = adb("shell", "getprop", "ro.hardware").strip()
    if "ranchu" not in hardware and "goldfish" not in hardware:
        raise RuntimeError("Refusing non-emulator hardware")
    if args.action == "prepare":
        for apk in [ROOT / "app/build/outputs/apk/debug/app-debug.apk",
                    ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
            print(adb("install", "-r", str(apk)))
        print(adb("shell", "pm", "grant", "com.Ling.actant", "android.permission.POST_NOTIFICATIONS"))
        component = "com.Ling.actant/com.example.myapplication.device.DeviceAccessibilityService"
        existing = adb("shell", "settings", "get", "secure", "enabled_accessibility_services").strip()
        components = [] if existing in ("null", "") else existing.split(":")
        # Instrumentation may leave an enabled service in CrashedServices; rebind it.
        remaining = [item for item in components if item != component]
        if remaining:
            adb("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(remaining))
        else:
            adb("shell", "settings", "delete", "secure", "enabled_accessibility_services")
        time.sleep(0.5)
        if component not in components:
            components.append(component)
        print(adb("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(components)))
        print(adb("shell", "settings", "put", "secure", "accessibility_enabled", "1"))
        print(adb("shell", "am", "start", "-n", "com.Ling.actant/com.example.myapplication.MainActivity"))
    elif args.action == "tests":
        target = "com.example.myapplication.DeviceControlInstrumentedTest" + ("#" + args.method if args.method else "")
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", target,
                     "com.Ling.actant.test/androidx.test.runner.AndroidJUnitRunner", timeout=180)
        (OUT / ("instrumentation-" + (args.method or "all") + ".txt")).write_text(result, encoding="utf-8")
        print(result)
        if "FAILURES" in result or "INSTRUMENTATION_FAILED" in result or "shortMsg=" in result:
            raise SystemExit(1)
    elif args.action == "diagnostics":
        print(adb("shell", "settings", "get", "secure", "enabled_accessibility_services"))
        text = adb("shell", "dumpsys", "accessibility")
        (OUT / "accessibility-dump.txt").write_text(text, encoding="utf-8")
        for line in text.splitlines():
            if any(word in line.lower() for word in ("actant", "deviceaccessibility", "enabled", "bound", "crash", "automation", "binding")):
                print(line)
        logs = adb("logcat", "-d", "-t", "3000")
        (OUT / "recent-log.txt").write_text(logs, encoding="utf-8")
        for line in logs.splitlines():
            if ("Accessibility" in line or "AndroidRuntime" in line or "Shizuku" in line) and "Uwb" not in line:
                print(line[:1000])
    elif args.action == "launch":
        print(adb("shell", "am", "start", "-n", "com.Ling.actant/com.example.myapplication.MainActivity"))
    else:
        text = adb("logcat", "-d", "-b", "crash")
        (OUT / "crash.txt").write_text(text, encoding="utf-8")
        print(text[-14000:])


if __name__ == "__main__":
    main()

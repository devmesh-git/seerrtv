# How to Capture Logs for SeerrTV (Chromecast / Google TV)

If SeerrTV is not working as expected, logs help us diagnose and fix the issue faster.

## 1) Enable Developer Mode

On your Chromecast / Google TV:

- Go to **Settings > System > About**
- Scroll to **Android TV OS Build**
- Select it **7 times** until Developer Mode is enabled

## 2) Enable Debugging

- Go to **Settings > System > Developer options**
- Turn on:
  - **USB debugging**
  - **Network debugging**

## 3) Find Your Device IP Address

- Go to **Settings > Network & Internet**
- Select your current Wi-Fi network
- Note the IP address (for example: `192.168.x.x`)

## 4) Connect from Your Computer

Install ADB first if needed ([Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools)).

Then open a terminal (or Command Prompt on Windows).

### macOS / Linux

```bash
adb connect YOUR_IP_HERE
adb logcat -c
adb shell pidof ca.devmesh.seerrtv | xargs -I {} adb logcat --pid={} > seerrtv_logs.txt
```

### Windows (Command Prompt)

```bat
adb connect YOUR_IP_HERE
adb logcat -c
adb shell pidof ca.devmesh.seerrtv
```

Copy the PID shown in the last command, then run:

```bat
adb logcat --pid=PASTE_PID_HERE > seerrtv_logs.txt
```

## 5) Reproduce the Issue

- Open SeerrTV
- Perform the steps that trigger the issue
- Let it run for about 30 seconds

Then stop logging with `Ctrl + C` in the terminal.

## 6) Send the Logs

Send `seerrtv_logs.txt` in Discord (or share it directly with the team).

## Tips

- Keep captures short to reduce unrelated log noise
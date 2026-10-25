# TVText 2.0.8 user guide

TVText sends text from Windows or an Android phone/tablet to an open text field on Google TV. The TV keeps its current keyboard, including Gboard. Both devices must be on the same Wi-Fi network.

## Install

- Install `TVText-TV-2.0.8.apk` on the TV.
- Install `TVText-Mobile-2.0.8.apk` on an Android 9+ phone or tablet if you want to send from Android.
- Keep `TVText-2.0.8.exe` and `TVText-2.0.8.exe.config` together on Windows. .NET Framework 4.8 is required; no administrator rights are needed.

## First setup on the TV

1. Open TVText. If it shows **Accessibility is off**, choose **Open accessibility settings** and enable **TVText — device input**. Return to TVText. The service is needed for insertion; TVText does not read the field contents.
2. Choose **Connect device**. The same button is used when pairing a phone or tablet. A temporary eight-digit code appears.
3. Leave the code screen open while pairing. **Cancel connection** closes only the temporary code; existing paired clients keep access.

## Pair from Windows

1. Start `TVText-2.0.8.exe` and select the discovered TV.
2. Click **Connect** and enter the eight-digit code shown on the TV.
3. Compare all four groups of the certificate fingerprint with the TV and confirm the match.
4. Open an app on the TV, focus its text field, and show the on-screen keyboard.

Enter or **Send to TV** sends the text. **Shift+Enter** inserts a new line. TVText inserts at the current cursor position and does not press Search, Enter, OK, or other buttons on the TV.

Android 13+ uses the accessibility input connection. Android 9–12 uses the field's **Paste** action: text is inserted at the cursor or replaces the selected range. If the field does not support Paste or rejects it, sending fails and the sender keeps the draft. TVText never falls back to replacing the entire field or reading its contents. Custom editors may not support either insertion method; see the [compatibility checklist](../testing/COMPATIBILITY.md).

## Pair from Android

1. Open TVText-Mobile and wait for the TV to appear, or tap refresh.
2. Select the TV and tap **Connect**.
3. On the TV choose **Connect device**, enter the code in the phone/tablet, and compare the fingerprint shown by both apps.
4. Confirm the pairing, focus the input field and keyboard on the TV, enter text on the phone/tablet, and tap **Send to TV**.

The mobile list can contain several TVs. **Rename** changes a name only on that phone/tablet. **Unpair** removes that phone's key; if the TV could not be notified, remove the same entry from **Device list** on the TV.

## Multiple TVs and changed addresses

The open list shows the discovered TVs and marks the selected one. Paired keys are stored separately, so text goes only to the selected TV. No manual IP address is required. mDNS finds the current address; after an address change, the client verifies the same installation ID and pinned certificate before using it. A normal DHCP change does not require pairing again.

Windows keeps local TV aliases in the current user's `%LOCALAPPDATA%\TVText` profile. The Android app stores encrypted profiles in its private no-backup storage.

## Network transport

TVText uses IPv4 for discovery and text delivery. The TV receiver advertises and accepts IPv4 only; no network-wide Wi-Fi setting is changed.

## If a TV is not found

- Make sure TVText is open on the TV, the accessibility service is enabled, and both devices use the same Wi-Fi network.
- Guest networks, VPNs, multicast filtering, and client isolation can block mDNS.
- Refresh the TV list. After a failed send, the client performs one bounded discovery refresh; it does not automatically resend the text.
- If the TV service is stuck, turn **TVText — device input** off and on in the accessibility settings, then reopen TVText.
- If pairing or certificate verification fails, choose **Connect** again and use a new code. If a field is unavailable, open its keyboard and try again.

Discovery uses mDNS/DNS-SD on UDP 5353. Text uses TLS on TCP 45831.

## Sending behavior and limits

Unicode, Cyrillic, emoji, and links are supported. One send is limited to 32 KiB of UTF-8. A confirmed send clears the submitted draft. A timeout, error, or lost confirmation preserves the draft because the text may already have reached the TV; check the TV before trying again. TVText never replays an ambiguous request automatically.

The TV application hands text to the active Android input field. A vendor application or keyboard may filter, transform, or reject it. If there is no active editable field, the request is rejected and not queued.

## Updates and privacy

TVText-TV 2.0.8 can update 2.0.7 without clearing TV data. Since 2.0.6, the mobile package is `com.tvtext.mobile.app`; it is a new application compared with older mobile builds, while 2.0.8 can update an existing 2.0.6 or 2.0.7 installation. Pairings and settings remain when the same application ID, signing key and application data are preserved. Versions 1.4–1.8 are compatible with the current TV protocol; versions 1.3 and earlier require a new pairing because of the TLS identity change.

Text is sent directly over the local network with TLS certificate pinning. There is no cloud service, account, or send history. Windows credentials are protected with DPAPI; Android profiles use Android Keystore AES-GCM. TVText does not read or store the contents of the input field.

# Radio Relay

Watches a specific group chat on your phone and automatically texts the right keyword to a radio station's call-in line when someone posts a code like `98... Minnie`.

## How it works

Radio stations run on-air contests where listeners text a keyword to a station's phone number to enter. In a group chat, people share the code as it's announced on air — something like:

```
98... Minnie
96... Chad
94... Dona
```

Radio Relay watches your chosen group chat (via Android's notification system — it doesn't need to be the default messaging app), recognizes lines like these, and automatically sends an SMS with just the keyword ("Minnie", "Chad", "Dona") to the matching station number, so you don't have to retype and send it yourself every time.

By default it ships with:

| Starts with | Texts to |
|---|---|
| 98 | (916) 909-0985 |
| 96 | (916) 909-0969 |
| 94 | (916) 909-0947 |

You can add, remove, or edit these in the app.

## Install

### Option A — Download the built APK (easiest)

1. Open the **Actions** tab on this repo.
2. Click into the latest **Build APK** run and wait for it to finish (a few minutes).
3. Scroll to the bottom and download the **RadioRelay-apk** artifact — it's a zip containing `app-debug.apk`.
4. Transfer the APK to your phone (email it to yourself, use a cloud drive, or plug in a USB cable) and tap it to install.
5. Android will warn about installing from an unknown source — allow it for this one file.

### Option B — Build it yourself in Android Studio

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run the app on your phone (USB debugging, or build a release APK via **Build → Generate Signed Bundle / APK**).

## First-time setup

1. Open the app.
2. Under **Setup**, turn on **Notification access** and allow **SMS**.
   - If the toggle looks greyed out or Android calls it a "restricted setting," open the app's **App info** (there's a shortcut button for this), tap the **⋮** menu in the top right, choose **Allow restricted settings**, then try again.
3. Under **Group chat to watch**, type (or tap a suggestion) for the exact name of the group chat as it appears in your messaging app's notifications.
4. Check the **Station lines** list matches the contest you're following, and adjust the cooldown if several people tend to post the same code back-to-back (this stops it from double-texting).
5. Tap **Save settings**, then flip the main switch on.
6. Optional: paste a sample message into **Try a message** to preview what it would send, without actually sending anything.

From then on, Radio Relay runs quietly in the background. Each time it sends a text, it'll (optionally) show you a quick notification confirming what went out, and you can review everything it's done in the **Activity** log.

## Notes & troubleshooting

- This reads notifications, not the raw contents of your messaging app's database, so it only "sees" what shows up as a notification. Make sure notifications for that chat aren't muted.
- It only starts watching for new messages from the moment you turn it on — it won't react to anything already in the chat.
- If a text fails to send (no signal, airplane mode, etc.), the Activity log will say why.
- Nothing here is affiliated with any radio station — it's just a convenience tool for relaying a code someone already posted in a chat you're in.

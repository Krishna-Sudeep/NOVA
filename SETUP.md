# Nova v3 setup (beginner steps)

## A. Backend (one time, ~10 min)
1. console.anthropic.com: make an account, add a few dollars, create an API key, copy it.
2. cloudflare.com: sign up free > Workers & Pages > Create > Hello World > Deploy > Edit code > delete all, paste backend/worker.js > Deploy.
3. Worker > Settings > Variables and Secrets > add secrets:
   ANTHROPIC_API_KEY = your key
   NOVA_TOKEN = any long password you invent
   YOUTUBE_API_KEY = (optional, makes "play X" start the video itself) see step 5
4. Copy the Worker address (https://something.workers.dev).
5. Optional YouTube key: console.cloud.google.com > new project > APIs & Services > Library > "YouTube Data API v3" > Enable > Credentials > Create API key.

## B. Phone
1. Android Studio: open the Nova folder, plug in your phone (USB debugging on), press Run.
2. Open Nova > Settings: paste backend address + password, your country code (e.g. 91), Save.
3. Allow every permission it asks. Tap "Setup check" until it shows ✅.
4. Hands-free: turn on the switch. Allow "Display over other apps".
5. Phone Settings > Apps > Nova > Battery: Unrestricted. Turn on Autostart and "Display pop-ups while running in background" (iQOO Funtouch OS).
6. WhatsApp auto-send (optional): App info > Nova > ⋮ menu > "Allow restricted settings", then Settings > Accessibility > Nova > turn on.

## Try these
"Nova, play Arijit Singh on YouTube"
"Nova, message Sorry on WhatsApp saying I'm late" (then say yes)
"Nova, open YouTube and then set a timer for 10 minutes"

# v4 additions
## Locked phone / screen off (read this)
- Hands-free keeps listening with the screen off (foreground service + wake lock). Not verified on a real phone yet.
- Android will NOT let any app open WhatsApp/YouTube over a locked screen. For those, Nova asks Android to unlock:
  no lock or Smart Lock (Settings > Security > Smart Lock / trusted device or place) = automatic;
  face/fingerprint/PIN = you authenticate once, Nova then finishes the request.
- Calls, alarms, timers, flashlight and volume do not need unlocking.
## WhatsApp
- "Hey Nova, send Sai a WhatsApp message saying hey bro, are you free?" sends with no confirmation question.
- Needs the Accessibility helper ON (step B6) or you must tap Send yourself. Nova only says "Done" after it tapped Send.
- Two contacts with the same name: Nova asks for the full name. Settings has "Ask before sending messages" if you want a safety yes/no.
## Android 15 checklist (Setup check shows these)
Battery Unrestricted, Display over other apps, Full-screen notifications (Settings > Apps > Nova > Notifications), Accessibility helper.

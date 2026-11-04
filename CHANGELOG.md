## Fixed
- Nicknames in server-formatted chat (such as Hypixel) are now wrapped in NiChat brackets, matching other servers: `[MVP+] [Nick]: message`.
- Translation no longer floods Google Translate in busy chats: requests are sent one at a time, stale messages are skipped, and translation pauses after errors such as rate limiting (HTTP 429).
- Messages without Latin letters are no longer sent for translation when the target language uses Cyrillic.

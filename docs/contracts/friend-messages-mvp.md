# Friend account messaging MVP

Use the existing Authorization: Bearer token on every endpoint. GET /api/v2/me initializes the local account record. The recipient must have signed in at least once. Friend addition uses an exact account ID and is one-way in this MVP. No phone search or approval flow.

| Method and path (under /api/v2) | Behavior |
| --- | --- |
| PUT /friends | JSON {"accountId":"demo-recipient"} adds an active account to the caller's friend list. |
| GET /friends | Returns [{"accountId":"demo-recipient"}]. |
| POST /friend-messages | Multipart parts message (application/json) and audio (PCM WAV). Send a UUID Idempotency-Key and reuse it for retries. Returns the stored message, including id. |
| GET /friend-messages/inbox | Lists unplayed, unexpired messages for the signed-in recipient. |
| GET /friend-messages/sent | Lists the caller's sent messages. completedAt is set only after a completion report. |
| GET /friend-messages/{id}/audio | Returns WAV bytes to the recipient while the message is available. |
| PUT /friend-messages/{id}/completion | Recipient reports completed playback. Retries preserve the original completion and retention timestamps. |
| DELETE /friend-messages/{id} | Recipient removes a message. It immediately disappears from inbox/archive and audio access is revoked. Storage is cleared by scheduled cleanup. |
| GET /friend-messages/archive | Lists listened messages still within the seven-day retention period. |

Example message part:

```json
{"recipientId":"demo-recipient","placeName":"Test pin","address":"Optional","latitude":37,"longitude":127,"listeningMode":"PRIVATE"}
```

expiresAt is optional Unix milliseconds. It defaults to 24 hours after sending. createdAt, completedAt, and archiveUntil also use Unix milliseconds. Coordinates represent the selected search result, available through GET /api/v2/places.

At the selected pin, with gear P and doors closed, the vehicle app plays TOGETHER automatically and shows Listen/Delete for PRIVATE. The Android team owns the pin tolerance and vehicle state checks. Call completion after the audio finishes. There is no vehicle token, pre-departure session, navigation target match or recurring weekday in this API. The server authenticates the recipient and stores playback reports. Vehicle state is checked by the vehicle client.

The existing vehicle/session routes remain available. The web and vehicle clients still need to call this contract. Permanent archiving, reactions, production sign-in UI and shared-device logout behavior remain follow-up work. Local H2 initializes schema-friends.sql automatically. For Supabase, apply migration 003 after 001 and 002 before using this build. No remote migration was applied here.

Run gradlew.bat test --tests "*FamilyApiTests" from services/api. The test uses synthetic WAV bytes and local demo accounts.
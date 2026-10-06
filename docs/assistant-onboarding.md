# Welcome, consent and assistant chat (UI)

Module: `feature/Assistant`. These three screens follow phone verification:

`verify phone → Welcome → Consent → Assistant chat → home`

| Screen | What it does |
|---|---|
| **Welcome** | Introduces the assistant. "Yes, I'm ready!" stays disabled until the terms box is ticked. |
| **Consent** | Explains how the assistant uses what the user shares; "Let's get started" stays disabled until the consent box is ticked. Back returns to Welcome; continuing replaces the stack so Back cannot return here. |
| **Chat** | Header (avatar, name, status), progress strip, message list, typing indicator, input bar. On wide windows it floats as a rounded card. |

The layouts follow the reference screenshots (welcome / data consent / chat) in this app's own palette and Poppins type. The reference's mascot, logo, photography and legal wording were **not** reused.

## Connecting the AI

The chat UI only talks to `AssistantGateway` (`domain/gateway/AssistantGateway.kt`):

```kotlin
interface AssistantGateway {
    suspend fun greet(): AssistantReply                      // opening message
    suspend fun reply(history: List<ChatMessage>): AssistantReply   // answer to the conversation so far
}
data class AssistantReply(val text: String, val progress: Float, val finished: Boolean = false)
```

1. Implement it against your model/backend. Throw on failure: the chat keeps the conversation, shows "couldn't connect" and offers *Try again*. Do not swallow `CancellationException`.
2. Bind it in `di/AssistantModule.kt`, replacing `ScriptedAssistantGateway` (a stand-in that asks five fixed questions, in Vietnamese).
3. `text` may contain several paragraphs separated by a blank line; they render in one bubble. `progress` (0..1) drives the progress strip; `finished = true` swaps the input for the continue button.

Security note for when you do: the model must be called from **your server**, never directly from the app (an API key in the app is public), and the conversation should be tied to the signed-in user (`Authorization: Bearer`, as in `AuthRemoteApi`).

## Before release

- **Consent text is a draft** (`consent_body` in `composeResources/values*/strings.xml`). It makes no promises about vendors, retention or training; replace it with wording approved by whoever owns your privacy obligations.
- **Terms / Privacy links are styled but not tappable**: there are no published documents to open yet.
- **Consent is not recorded.** The tick only gates the UI. Store who consented, to which text version, and when, on the server.
- The assistant name ("Tana"), subtitle and avatar (a pink sparkle disc) are placeholders.
- The Welcome/Consent flow is only reached after sign-up; a returning user who logs in goes straight to home.

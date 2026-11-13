package com.aura.browser.feature.browser.engine

import android.content.Context
import android.net.Uri
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/**
 * Bridges GeckoView page prompts (alert/confirm/prompt, `<select>`, file inputs, HTTP auth, popups)
 * to Compose dialogs through a [BrowserPromptQueue].
 */
internal class AuraPromptDelegate(
    private val appContext: Context,
    private val queue: BrowserPromptQueue,
) : GeckoSession.PromptDelegate {

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt,
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        queue.enqueue(
            BrowserPrompt.Alert(
                id = queue.nextId(),
                title = prompt.title.orEmpty(),
                message = prompt.message.orEmpty(),
                onDismiss = { result.complete(prompt.dismiss()) },
            )
        )
        return result
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt,
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        queue.enqueue(
            BrowserPrompt.Confirm(
                id = queue.nextId(),
                title = prompt.title.orEmpty(),
                message = prompt.message.orEmpty(),
                onResult = { accepted ->
                    result.complete(
                        if (accepted) {
                            prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)
                        } else {
                            prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE)
                        }
                    )
                },
            )
        )
        return result
    }

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.TextPrompt,
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        queue.enqueue(
            BrowserPrompt.TextInput(
                id = queue.nextId(),
                title = prompt.title.orEmpty(),
                message = prompt.message.orEmpty(),
                defaultValue = prompt.defaultValue.orEmpty(),
                onResult = { text ->
                    result.complete(if (text == null) prompt.dismiss() else prompt.confirm(text))
                },
            )
        )
        return result
    }

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt,
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        val multiple = prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE
        val options = mutableListOf<ChoiceOption>()

        fun flatten(choices: Array<GeckoSession.PromptDelegate.ChoicePrompt.Choice>?, depth: Int) {
            choices?.forEach { choice ->
                val children = choice.items
                if (choice.separator) {
                    options += ChoiceOption(choice.id, "", enabled = false, selected = false, isSeparator = true)
                } else if (children != null && children.isNotEmpty()) {
                    options += ChoiceOption(choice.id, choice.label.orEmpty(), enabled = false, selected = false, isHeader = true)
                    flatten(children, depth + 1)
                } else {
                    options += ChoiceOption(
                        id = choice.id,
                        label = choice.label.orEmpty(),
                        enabled = !choice.disabled,
                        selected = choice.selected,
                    )
                }
            }
        }
        flatten(prompt.choices, 0)

        queue.enqueue(
            BrowserPrompt.Choice(
                id = queue.nextId(),
                title = prompt.title.orEmpty(),
                options = options,
                multiple = multiple,
                onResult = { selected ->
                    result.complete(
                        when {
                            selected == null || selected.isEmpty() -> prompt.dismiss()
                            multiple -> prompt.confirm(selected.toTypedArray())
                            else -> prompt.confirm(selected.first())
                        }
                    )
                },
            )
        )
        return result
    }

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
        queue.enqueue(
            BrowserPrompt.FilePick(
                id = queue.nextId(),
                mimeTypes = prompt.mimeTypes?.toList().orEmpty(),
                multiple = multiple,
                onResult = { uris: List<Uri>? ->
                    result.complete(
                        when {
                            uris.isNullOrEmpty() -> prompt.dismiss()
                            multiple -> prompt.confirm(appContext, uris.toTypedArray())
                            else -> prompt.confirm(appContext, uris.first())
                        }
                    )
                },
            )
        )
        return result
    }

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt,
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        queue.enqueue(
            BrowserPrompt.Auth(
                id = queue.nextId(),
                title = prompt.title.orEmpty().ifBlank { "Sign in required" },
                message = prompt.message.orEmpty(),
                onResult = { user, pass ->
                    result.complete(
                        if (user == null || pass == null) prompt.dismiss() else prompt.confirm(user, pass)
                    )
                },
            )
        )
        return result
    }

    /** Pop-ups that were not triggered by a user gesture are always blocked. */
    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt,
    ): GeckoResult<PromptResponse>? = GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
}

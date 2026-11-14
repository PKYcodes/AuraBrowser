package com.aura.browser.feature.browser.engine

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy

/**
 * Answers GeckoView's WebExtension install/update permission prompts with a confirmation dialog.
 *
 * GeckoView never finishes `WebExtensionController.install()` unless a prompt delegate answers, which
 * shows up as an endless "Installing...". The delegate interface changed between GeckoView releases
 * (different method names and response types), so it is implemented as a dynamic proxy: every
 * `onInstall*` / `onUpdate*` / `onOptional*` callback is routed here and the response object is built
 * from the method's declared return type. That keeps the app compiling and working across versions.
 */
internal object AuraExtensionPromptDelegate {

    fun create(queue: BrowserPromptQueue): WebExtensionController.PromptDelegate {
        val handler = InvocationHandler { proxy, method, args ->
            when {
                method.declaringClass == Any::class.java -> when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> "AuraExtensionPromptDelegate"
                }
                isPromptCallback(method) -> handlePrompt(queue, method, args ?: emptyArray())
                else -> defaultValue(method.returnType)
            }
        }
        return Proxy.newProxyInstance(
            WebExtensionController.PromptDelegate::class.java.classLoader,
            arrayOf(WebExtensionController.PromptDelegate::class.java),
            handler,
        ) as WebExtensionController.PromptDelegate
    }

    private fun isPromptCallback(method: Method): Boolean =
        GeckoResult::class.java.isAssignableFrom(method.returnType) &&
            (method.name.startsWith("onInstall") ||
                method.name.startsWith("onUpdate") ||
                method.name.startsWith("onOptional"))

    private fun handlePrompt(queue: BrowserPromptQueue, method: Method, args: Array<Any?>): Any {
        val extensionName = args.filterIsInstance<WebExtension>().firstOrNull()?.metaData?.name ?: "this extension"
        val stringLists = args.filterIsInstance<Array<*>>().map { array -> array.filterIsInstance<String>() }
        val permissions = stringLists.flatten().distinct()

        val action = when {
            method.name.startsWith("onUpdate") -> "Update"
            method.name.startsWith("onOptional") -> "Allow more access for"
            else -> "Add"
        }

        val result = GeckoResult<Any>()
        queue.enqueue(
            BrowserPrompt.Confirm(
                id = queue.nextId(),
                title = "$action $extensionName?",
                message = describePermissions(permissions),
                onResult = { allow ->
                    val response = buildResponse(method, allow)
                    if (response != null) {
                        result.complete(response)
                    } else {
                        result.completeExceptionally(IllegalStateException("Unsupported prompt response type"))
                    }
                },
            )
        )
        return result
    }

    private fun describePermissions(permissions: List<String>): String {
        if (permissions.isEmpty()) return "This extension doesn\u2019t ask for any special permissions."
        val readable = permissions.map { permission ->
            when (permission) {
                "<all_urls>", "*://*/*", "http://*/*", "https://*/*" -> "Access your data on all websites"
                "tabs" -> "Read the list of open tabs"
                "webRequest", "webRequestBlocking" -> "Monitor and block network requests"
                "storage", "unlimitedStorage" -> "Store its own data"
                "cookies" -> "Read and change cookies"
                "history" -> "Read your browsing history"
                "downloads" -> "Manage downloads"
                "clipboardWrite" -> "Copy to the clipboard"
                "notifications" -> "Show notifications"
                "nativeMessaging" -> "Exchange messages with the app"
                else -> permission
            }
        }.distinct()
        return "It will be able to:\n\u2022 " + readable.joinToString("\n\u2022 ")
    }

    /** Builds the value expected by the callback's `GeckoResult<T>` return type. */
    private fun buildResponse(method: Method, allow: Boolean): Any? {
        val responseType = (method.genericReturnType as? ParameterizedType)
            ?.actualTypeArguments
            ?.firstOrNull() as? Class<*>
            ?: return null

        return when {
            responseType == AllowOrDeny::class.java -> if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY
            responseType == java.lang.Boolean::class.java -> allow
            else -> {
                // Response classes such as PermissionPromptResponse(isPermissionsGranted, isPrivateModeGranted, ...).
                // Only the first flag follows the user's choice; optional extras (private mode, telemetry) stay off.
                val constructor = responseType.constructors.firstOrNull { c ->
                    c.parameterTypes.isNotEmpty() && c.parameterTypes.all { it == java.lang.Boolean.TYPE }
                } ?: return null
                val values = Array<Any>(constructor.parameterTypes.size) { index -> index == 0 && allow }
                runCatching { constructor.newInstance(*values) }.getOrNull()
            }
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        else -> null
    }
}

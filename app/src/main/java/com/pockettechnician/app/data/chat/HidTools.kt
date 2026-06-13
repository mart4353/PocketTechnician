package com.pockettechnician.app.data.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * The HID tools exposed to the model and their JSON schemas. One source of
 * truth for tool names (used both in the API request and when dispatching an
 * accepted call to the HID layer) and the system-prompt description.
 */
object HidTools {

    const val TYPE_TEXT = "type_text"
    const val MOVE_POINTER_TO = "move_pointer_to"
    const val MOUSE_PRESS = "mouse_press"

    // Note: the plain-English description of these tools for the model lives in
    // assets/system_prompt.md. Keep that file in sync if tool names change.

    /** Anthropic Messages API `tools` array. */
    fun anthropicTools(): JSONArray = JSONArray().apply {
        put(anthropicTool(TYPE_TEXT, typeTextDescription, typeTextSchema()))
        put(anthropicTool(MOVE_POINTER_TO, movePointerToDescription, movePointerToSchema()))
        put(anthropicTool(MOUSE_PRESS, mousePressDescription, mousePressSchema()))
    }

    /** OpenAI / Grok chat-completions `tools` array. */
    fun openAiTools(): JSONArray = JSONArray().apply {
        put(openAiTool(TYPE_TEXT, typeTextDescription, typeTextSchema()))
        put(openAiTool(MOVE_POINTER_TO, movePointerToDescription, movePointerToSchema()))
        put(openAiTool(MOUSE_PRESS, mousePressDescription, mousePressSchema()))
    }

    private fun anthropicTool(name: String, description: String, schema: JSONObject): JSONObject =
        JSONObject()
            .put("name", name)
            .put("description", description)
            .put("input_schema", schema)

    private fun openAiTool(name: String, description: String, schema: JSONObject): JSONObject =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", schema),
            )

    private const val typeTextDescription =
        "Type the given text on the connected computer using the keyboard."
    private const val movePointerToDescription =
        "Move the mouse pointer to an absolute screen position (x, y) measured in pixels from the top-left corner. " +
            "The pointer is first homed into the top-left corner, so this jumps to a known location regardless of " +
            "where it was."
    private const val mousePressDescription =
        "Click a mouse button at the current pointer position."

    private fun typeTextSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject().put(
                "text",
                JSONObject().put("type", "string").put("description", "The text to type."),
            ),
        )
        .put("required", JSONArray().put("text"))

    private fun movePointerToSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("x", JSONObject().put("type", "integer").put("description", "Target X in pixels from the left edge."))
                .put("y", JSONObject().put("type", "integer").put("description", "Target Y in pixels from the top edge.")),
        )
        .put("required", JSONArray().put("x").put("y"))

    private fun mousePressSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject().put(
                "button",
                JSONObject()
                    .put("type", "string")
                    .put("enum", JSONArray().put("left").put("right"))
                    .put("description", "Which mouse button to click."),
            ),
        )
        .put("required", JSONArray().put("button"))
}

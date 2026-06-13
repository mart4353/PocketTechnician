package com.pockettechnician.app.hid

/**
 * HID report descriptor and report-encoding helpers for a combined
 * Bluetooth keyboard + mouse device.
 *
 * Two top-level report collections share one descriptor:
 *  - Report ID 1: boot-style keyboard (1 modifier byte, 1 reserved byte, 6 key slots)
 *  - Report ID 2: relative mouse (3 buttons + relative X/Y, signed -127..127)
 *
 * Sent via [android.bluetooth.BluetoothHidDevice.sendReport]; the report ID is
 * passed as the `id` argument, so the byte payloads below carry only the body.
 */
object HidDescriptors {

    const val REPORT_ID_KEYBOARD = 1
    const val REPORT_ID_MOUSE = 2

    /** Left Shift modifier bit, used for uppercase letters and shifted symbols. */
    private const val MOD_LEFT_SHIFT = 0x02

    /** Each relative mouse report carries a delta clamped to this range. */
    const val MOUSE_DELTA_LIMIT = 127

    /** Mouse button bitmask values (report byte 0). */
    const val MOUSE_BUTTON_LEFT = 0x01
    const val MOUSE_BUTTON_RIGHT = 0x02
    const val MOUSE_BUTTON_MIDDLE = 0x04

    val REPORT_MAP: ByteArray = byteArrayOf(
        // ----- Keyboard (Report ID 1) -----
        0x05.b, 0x01.b,             // Usage Page (Generic Desktop)
        0x09.b, 0x06.b,             // Usage (Keyboard)
        0xA1.b, 0x01.b,             // Collection (Application)
        0x85.b, 0x01.b,             //   Report ID (1)
        0x05.b, 0x07.b,             //   Usage Page (Keyboard/Keypad)
        0x19.b, 0xE0.b,             //   Usage Minimum (Left Control)
        0x29.b, 0xE7.b,             //   Usage Maximum (Right GUI)
        0x15.b, 0x00.b,             //   Logical Minimum (0)
        0x25.b, 0x01.b,             //   Logical Maximum (1)
        0x75.b, 0x01.b,             //   Report Size (1)
        0x95.b, 0x08.b,             //   Report Count (8)
        0x81.b, 0x02.b,             //   Input (Data, Var, Abs) -> modifier byte
        0x95.b, 0x01.b,             //   Report Count (1)
        0x75.b, 0x08.b,             //   Report Size (8)
        0x81.b, 0x01.b,             //   Input (Const) -> reserved byte
        0x95.b, 0x06.b,             //   Report Count (6)
        0x75.b, 0x08.b,             //   Report Size (8)
        0x15.b, 0x00.b,             //   Logical Minimum (0)
        0x25.b, 0x65.b,             //   Logical Maximum (101)
        0x05.b, 0x07.b,             //   Usage Page (Keyboard/Keypad)
        0x19.b, 0x00.b,             //   Usage Minimum (0)
        0x29.b, 0x65.b,             //   Usage Maximum (101)
        0x81.b, 0x00.b,             //   Input (Data, Array) -> 6 key slots
        0xC0.b,                     // End Collection

        // ----- Mouse (Report ID 2) -----
        0x05.b, 0x01.b,             // Usage Page (Generic Desktop)
        0x09.b, 0x02.b,             // Usage (Mouse)
        0xA1.b, 0x01.b,             // Collection (Application)
        0x85.b, 0x02.b,             //   Report ID (2)
        0x09.b, 0x01.b,             //   Usage (Pointer)
        0xA1.b, 0x00.b,             //   Collection (Physical)
        0x05.b, 0x09.b,             //     Usage Page (Buttons)
        0x19.b, 0x01.b,             //     Usage Minimum (Button 1)
        0x29.b, 0x03.b,             //     Usage Maximum (Button 3)
        0x15.b, 0x00.b,             //     Logical Minimum (0)
        0x25.b, 0x01.b,             //     Logical Maximum (1)
        0x95.b, 0x03.b,             //     Report Count (3)
        0x75.b, 0x01.b,             //     Report Size (1)
        0x81.b, 0x02.b,             //     Input (Data, Var, Abs) -> 3 buttons
        0x95.b, 0x01.b,             //     Report Count (1)
        0x75.b, 0x05.b,             //     Report Size (5)
        0x81.b, 0x01.b,             //     Input (Const) -> padding
        0x05.b, 0x01.b,             //     Usage Page (Generic Desktop)
        0x09.b, 0x30.b,             //     Usage (X)
        0x09.b, 0x31.b,             //     Usage (Y)
        0x15.b, 0x81.b,             //     Logical Minimum (-127)
        0x25.b, 0x7F.b,             //     Logical Maximum (127)
        0x75.b, 0x08.b,             //     Report Size (8)
        0x95.b, 0x02.b,             //     Report Count (2)
        0x81.b, 0x06.b,             //     Input (Data, Var, Rel) -> X, Y
        0xC0.b,                     //   End Collection
        0xC0.b,                     // End Collection
    )

    /** All-zero keyboard report: releases every key. */
    val KEYBOARD_RELEASE: ByteArray = ByteArray(8)

    /** Build an 8-byte keyboard report for a single key + modifier. */
    fun keyboardReport(modifier: Int, usage: Int): ByteArray =
        byteArrayOf(modifier.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0)

    /** Build a 3-byte relative mouse report (no buttons pressed). */
    fun mouseReport(dx: Int, dy: Int): ByteArray =
        byteArrayOf(0, dx.toByte(), dy.toByte())

    /** Build a 3-byte mouse report with the given button bitmask held and no motion. */
    fun mouseButtonReport(buttons: Int): ByteArray =
        byteArrayOf(buttons.toByte(), 0, 0)

    /**
     * Map an ASCII character to (modifier, USB HID usage code), or null if the
     * character is not supported. Covers everything in "Hello World!" plus the
     * common printable ASCII set.
     */
    fun charToKey(c: Char): Pair<Int, Int>? = when (c) {
        in 'a'..'z' -> 0 to (0x04 + (c - 'a'))
        in 'A'..'Z' -> MOD_LEFT_SHIFT to (0x04 + (c - 'A'))
        in '1'..'9' -> 0 to (0x1E + (c - '1'))
        '0' -> 0 to 0x27
        ' ' -> 0 to 0x2C
        '\n' -> 0 to 0x28      // Enter
        '\t' -> 0 to 0x2B      // Tab
        '-' -> 0 to 0x2D
        '=' -> 0 to 0x2E
        '[' -> 0 to 0x2F
        ']' -> 0 to 0x30
        '\\' -> 0 to 0x31
        ';' -> 0 to 0x33
        '\'' -> 0 to 0x34
        '`' -> 0 to 0x35
        ',' -> 0 to 0x36
        '.' -> 0 to 0x37
        '/' -> 0 to 0x38
        '!' -> MOD_LEFT_SHIFT to 0x1E
        '@' -> MOD_LEFT_SHIFT to 0x1F
        '#' -> MOD_LEFT_SHIFT to 0x20
        '$' -> MOD_LEFT_SHIFT to 0x21
        '%' -> MOD_LEFT_SHIFT to 0x22
        '^' -> MOD_LEFT_SHIFT to 0x23
        '&' -> MOD_LEFT_SHIFT to 0x24
        '*' -> MOD_LEFT_SHIFT to 0x25
        '(' -> MOD_LEFT_SHIFT to 0x26
        ')' -> MOD_LEFT_SHIFT to 0x27
        '_' -> MOD_LEFT_SHIFT to 0x2D
        '+' -> MOD_LEFT_SHIFT to 0x2E
        '{' -> MOD_LEFT_SHIFT to 0x2F
        '}' -> MOD_LEFT_SHIFT to 0x30
        '|' -> MOD_LEFT_SHIFT to 0x31
        ':' -> MOD_LEFT_SHIFT to 0x33
        '"' -> MOD_LEFT_SHIFT to 0x34
        '~' -> MOD_LEFT_SHIFT to 0x35
        '<' -> MOD_LEFT_SHIFT to 0x36
        '>' -> MOD_LEFT_SHIFT to 0x37
        '?' -> MOD_LEFT_SHIFT to 0x38
        else -> null
    }
}

/** Convenience: treat an Int literal as a descriptor byte (handles 0x80..0xFF). */
private val Int.b: Byte get() = this.toByte()

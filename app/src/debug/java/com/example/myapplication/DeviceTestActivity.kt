package com.example.myapplication

import android.app.Activity
import android.os.Bundle
import android.widget.*

/** Non-exported debug-only surface for repeatable emulator interaction checks. */
class DeviceTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 120, 40, 40) }
        val status = TextView(this).apply { text = "Device QA ready"; textSize = 22f }
        val input = EditText(this).apply { hint = "Device QA input"; contentDescription = "QA input"; setSingleLine() }
        val button = Button(this).apply {
            text = "Apply QA text"
            setOnClickListener { status.text = "Result: ${input.text}" }
        }
        layout.addView(status)
        layout.addView(input)
        layout.addView(button)
        val scroll = ScrollView(this)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        repeat(60) { index -> rows.addView(TextView(this).apply { text = "QA row $index"; textSize = 20f; setPadding(10, 20, 10, 20) }) }
        scroll.addView(rows)
        layout.addView(scroll)
        setContentView(layout)
    }
}

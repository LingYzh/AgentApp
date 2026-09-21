package com.example.myapplication.agent

/** Deliberately tiny exact command allow-list. Never treat a prefix as safe. */
object CommandPolicy {
    fun isLowRisk(command: String): Boolean {
        if (command.isBlank() || command.any { it == '\n' || it == '\r' }) return false
        if (Regex("[;&|`\\$<>\\\\()]").containsMatchIn(command)) return false
        val tokens = command.trim().split(Regex("\\s+"))
        if (tokens.isEmpty()) return false
        val executable = tokens.first()
        if (executable !in setOf("pwd", "ls", "stat", "wc")) return false
        return tokens.drop(1).all { token ->
            token == "--" || token.matches(Regex("-[A-Za-z]+")) ||
                token.matches(Regex("[A-Za-z0-9_./:=+,-]+"))
        }
    }
}

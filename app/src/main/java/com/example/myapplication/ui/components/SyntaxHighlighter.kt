package com.example.myapplication.ui.components

/** Small deterministic lexer for display only. It never evaluates code or HTML. */
internal enum class SyntaxKind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, TYPE, FUNCTION }

internal data class SyntaxToken(val text: String, val kind: SyntaxKind)

internal object SyntaxHighlighter {
    private val aliases = mapOf(
        "kt" to "kotlin", "kts" to "kotlin", "javascript" to "js", "jsx" to "js",
        "typescript" to "ts", "tsx" to "ts", "py" to "python", "sh" to "shell",
        "bash" to "shell", "zsh" to "shell", "yml" to "yaml", "xml" to "html"
    )
    private val languages = setOf("kotlin", "java", "js", "ts", "python", "shell", "json", "yaml", "html", "css", "sql")
    private val keywords = mapOf(
        "kotlin" to setOf("fun", "val", "var", "class", "object", "interface", "when", "if", "else", "return", "import", "package", "is", "in", "null", "true", "false", "suspend", "data"),
        "java" to setOf("class", "interface", "public", "private", "protected", "static", "final", "void", "new", "return", "if", "else", "switch", "case", "true", "false", "null", "package", "import"),
        "js" to setOf("const", "let", "var", "function", "class", "return", "if", "else", "async", "await", "import", "export", "from", "true", "false", "null", "undefined"),
        "ts" to setOf("const", "let", "var", "function", "class", "interface", "type", "return", "if", "else", "async", "await", "import", "export", "from", "true", "false", "null", "undefined"),
        "python" to setOf("def", "class", "return", "if", "elif", "else", "for", "while", "in", "import", "from", "as", "True", "False", "None", "async", "await", "with", "lambda"),
        "shell" to setOf("if", "then", "fi", "for", "in", "do", "done", "case", "esac", "function", "export", "local"),
        "json" to setOf("true", "false", "null"),
        "yaml" to setOf("true", "false", "null", "yes", "no"),
        "html" to setOf("html", "head", "body", "div", "span", "details", "summary", "script", "style", "a", "p", "button", "input"),
        "css" to setOf("@media", "@keyframes", "important"),
        "sql" to setOf("select", "from", "where", "join", "left", "right", "inner", "outer", "on", "insert", "into", "update", "delete", "create", "table", "alter", "drop", "as", "and", "or", "null", "order", "by", "group", "limit")
    )
    private val types = setOf("Int", "Long", "Float", "Double", "Boolean", "String", "Unit", "Any", "List", "Map", "Set", "StringBuilder", "void", "int", "long", "float", "double", "boolean", "char")

    fun tokens(language: String, source: String): List<SyntaxToken> {
        val normalized = aliases[language.trim().lowercase()] ?: language.trim().lowercase()
        if (normalized !in languages) return listOf(SyntaxToken(source, SyntaxKind.PLAIN))
        val languageKeywords = keywords[normalized].orEmpty()
        val result = mutableListOf<SyntaxToken>()
        val plain = StringBuilder()
        var index = 0
        fun add(text: String, kind: SyntaxKind) {
            if (text.isEmpty()) return
            if (kind == SyntaxKind.PLAIN) {
                plain.append(text)
            } else {
                if (plain.isNotEmpty()) {
                    result += SyntaxToken(plain.toString(), SyntaxKind.PLAIN)
                    plain.clear()
                }
                result += SyntaxToken(text, kind)
            }
        }
        while (index < source.length) {
            val start = index
            val lineComment = when (normalized) {
                "python", "shell", "yaml" -> source[index] == '#'
                "sql" -> source.startsWith("--", index)
                else -> source.startsWith("//", index)
            }
            if (lineComment) {
                index = source.indexOf('\n', index).let { if (it < 0) source.length else it }
                add(source.substring(start, index), SyntaxKind.COMMENT)
                continue
            }
            if (source.startsWith("/*", index)) {
                index = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
                add(source.substring(start, index), SyntaxKind.COMMENT)
                continue
            }
            if (normalized == "html" && source.startsWith("<!--", index)) {
                index = source.indexOf("-->", index + 4).let { if (it < 0) source.length else it + 3 }
                add(source.substring(start, index), SyntaxKind.COMMENT)
                continue
            }
            if (source[index] in charArrayOf('\'', '"', '`')) {
                val quote = source[index]
                index++
                while (index < source.length) {
                    if (source[index] == '\\') index = (index + 2).coerceAtMost(source.length)
                    else if (source[index++] == quote) break
                }
                add(source.substring(start, index), SyntaxKind.STRING)
                continue
            }
            if (source[index].isDigit()) {
                index++
                while (index < source.length && (source[index].isLetterOrDigit() || source[index] in charArrayOf('.', '_', 'x', 'X'))) index++
                add(source.substring(start, index), SyntaxKind.NUMBER)
                continue
            }
            if (source[index].isLetter() || source[index] == '_' || (normalized == "css" && source[index] == '@')) {
                index++
                while (index < source.length && (source[index].isLetterOrDigit() || source[index] in charArrayOf('_', '-'))) index++
                val word = source.substring(start, index)
                var nextIndex = index
                while (source.getOrNull(nextIndex)?.isWhitespace() == true) nextIndex++
                val next = source.getOrNull(nextIndex)
                val kind = when {
                    languageKeywords.any { it.equals(word, ignoreCase = true) } -> SyntaxKind.KEYWORD
                    word in types || word.firstOrNull()?.isUpperCase() == true -> SyntaxKind.TYPE
                    next == '(' || (normalized == "css" && next == ':') -> SyntaxKind.FUNCTION
                    else -> SyntaxKind.PLAIN
                }
                add(word, kind)
                continue
            }
            index++
            add(source.substring(start, index), SyntaxKind.PLAIN)
        }
        if (plain.isNotEmpty()) result += SyntaxToken(plain.toString(), SyntaxKind.PLAIN)
        return result
    }
}

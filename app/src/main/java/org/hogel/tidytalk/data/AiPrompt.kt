package org.hogel.tidytalk.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Locale-independent human-readable size formatter for prompt text. UI display
 * still uses android.text.format.Formatter via [org.hogel.tidytalk.ui.formatSize].
 */
fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var idx = 0
    while (value >= 1024 && idx < units.size - 1) {
        value /= 1024
        idx++
    }
    return String.format(Locale.US, "%.1f %s", value, units[idx])
}

/**
 * Builds the prompt the user copy-pastes to their chat AI. Each file gets a
 * 1-based ID; the AI is asked to echo IDs to delete inside a fenced code block.
 */
fun buildAiPrompt(targetDir: File, files: List<File>): String {
    val rootPath = targetDir.absolutePath
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return buildString {
        append("TidyTalk からの掃除相談です。以下のファイル一覧から、削除しても良さそうなもの\n")
        append("（古いダウンロード、明らかなゴミ、重複疑い等）を判断してください。\n")
        append("回答は、削除推奨ファイルの番号 ID をカンマか改行区切りでコードブロック\n")
        append("(```...```) に入れて返してください。理由のコメントは自由ですが、ID の\n")
        append("コードブロックは必ず含めてください。\n\n")
        append("対象: ${targetDir.name}（容量上位 ${files.size} 件まで）\n")
        append("--\n")
        files.forEachIndexed { i, f ->
            val rel = f.absolutePath.removePrefix("$rootPath/").ifEmpty { f.name }
            val size = humanBytes(f.length())
            val date = dateFmt.format(Date(f.lastModified()))
            append("[${i + 1}] $size  $date  $rel\n")
        }
    }
}

sealed interface AnswerParseResult {
    data object NoCodeBlock : AnswerParseResult
    data class Ok(val validIds: List<Int>, val invalidIds: List<Int>) : AnswerParseResult
}

private val codeBlockRegex = Regex("""```[\s\S]*?```""")
private val intRegex = Regex("""\d+""")

/**
 * Pulls IDs out of the first fenced code block in [answer]. IDs outside
 * `1..maxId` are reported as invalid so the UI can show what was dropped.
 */
fun parseAnswerIds(answer: String, maxId: Int): AnswerParseResult {
    val block = codeBlockRegex.find(answer) ?: return AnswerParseResult.NoCodeBlock
    val all = intRegex.findAll(block.value).mapNotNull { it.value.toIntOrNull() }.distinct().toList()
    val (valid, invalid) = all.partition { it in 1..maxId }
    return AnswerParseResult.Ok(valid, invalid)
}

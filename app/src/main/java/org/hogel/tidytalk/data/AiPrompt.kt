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

/** Default instruction text shown above the auto-generated file list. */
const val DEFAULT_AI_INSTRUCTION =
    "TidyTalk からの掃除相談です。以下のファイル一覧から、削除しても良さそうなもの\n" +
        "（古いダウンロード、明らかなゴミ、重複疑い等）を判断してください。\n" +
        "回答は、削除推奨ファイルの番号 ID をカンマか改行区切りでコードブロック\n" +
        "(```...```) に入れて返してください（チャット UI のコピー機能がそのまま\n" +
        "使えます）。理由のコメントは自由です。"

/** Default instruction text for the installed-apps AI flow. */
const val DEFAULT_APPS_AI_INSTRUCTION =
    "TidyTalk からの掃除相談です。以下のインストール済みアプリ一覧から、\n" +
        "アンインストールしても良さそうなもの（使ってなさそうな大きいアプリ、\n" +
        "重複機能のアプリ、長く触っていないと思われるアプリ等）を判断してください。\n" +
        "回答は、アンインストール推奨アプリの番号 ID をカンマか改行区切りで\n" +
        "コードブロック (```...```) に入れて返してください。理由のコメントは自由です。"

/**
 * Builds the prompt listing installed apps with 1-based IDs. The format mirrors
 * [buildAiPrompt] so the parsing path ([parseAnswerIds]) is shared.
 */
fun buildAppsAiPrompt(instruction: String, apps: List<AppEntry>): String {
    return buildString {
        append(instruction.trimEnd())
        append("\n\n")
        append("対象: インストール済みアプリ（容量上位 ${apps.size} 件まで）\n")
        append("--\n")
        apps.forEachIndexed { i, app ->
            append("[${i + 1}] ${humanBytes(app.totalBytes)}  ${app.label}  (${app.packageName})\n")
        }
    }
}

/**
 * Builds the prompt the user copy-pastes to their chat AI. [instruction] is the
 * editable lead-in; the auto-generated file list follows with 1-based IDs the
 * AI echoes back inside a fenced code block.
 */
fun buildAiPrompt(instruction: String, targetDir: File, files: List<File>): String {
    val rootPath = targetDir.absolutePath
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return buildString {
        append(instruction.trimEnd())
        append("\n\n")
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
    data object NoIds : AnswerParseResult
    data class Ok(val validIds: List<Int>, val invalidIds: List<Int>) : AnswerParseResult
}

private val codeBlockRegex = Regex("""```[\s\S]*?```""")
private val intRegex = Regex("""\d+""")

/**
 * Pulls IDs out of [answer]. Prefers the first fenced code block (the format
 * the prompt asks the AI to use); falls back to scanning the whole text so
 * that pasting just the code-block contents — what most chat UIs' Copy
 * buttons return — also works. IDs outside `1..maxId` are reported as invalid
 * so the UI can show what was dropped.
 */
fun parseAnswerIds(answer: String, maxId: Int): AnswerParseResult {
    val source = codeBlockRegex.find(answer)?.value ?: answer
    val all = intRegex.findAll(source).mapNotNull { it.value.toIntOrNull() }.distinct().toList()
    if (all.isEmpty()) return AnswerParseResult.NoIds
    val (valid, invalid) = all.partition { it in 1..maxId }
    return AnswerParseResult.Ok(valid, invalid)
}

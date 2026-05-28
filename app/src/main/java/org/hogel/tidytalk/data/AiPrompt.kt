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
        "回答は、削除推奨ファイルの ID（行頭の英数字 6〜8 文字）をカンマか改行区切り\n" +
        "でコードブロック (```...```) に入れて返してください（チャット UI のコピー\n" +
        "機能がそのまま使えます）。理由のコメントは自由です。"

/** A prompt-time mapping from stable ID to the item it describes. */
data class PromptItem<T>(val id: String, val value: T)

private fun <T> buildSnapshot(values: List<T>, key: (T) -> String): List<PromptItem<T>> {
    val ids = assignStableIds(values.map(key))
    return values.map { PromptItem(ids.getValue(key(it)), it) }
}

/** Snapshot for the file AI flow; ID is derived from each file's absolute path. */
fun fileSnapshot(files: List<File>): List<PromptItem<File>> =
    buildSnapshot(files) { it.absolutePath }

/** Snapshot for the apps AI flow; ID is derived from each app's package name. */
fun appsSnapshot(apps: List<InstalledApp>): List<PromptItem<InstalledApp>> =
    buildSnapshot(apps) { it.packageName }

/**
 * Builds the prompt the user copy-pastes to their chat AI. [instruction] is the
 * editable lead-in; the auto-generated file list follows with stable IDs the
 * AI echoes back inside a fenced code block.
 */
fun buildAiPrompt(instruction: String, targetDir: File, snapshot: List<PromptItem<File>>): String {
    val rootPath = targetDir.absolutePath
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return buildString {
        append(instruction.trimEnd())
        append("\n\n")
        append("対象: ${targetDir.name}（容量上位 ${snapshot.size} 件まで）\n")
        append("--\n")
        snapshot.forEach { (id, f) ->
            val rel = f.absolutePath.removePrefix("$rootPath/").ifEmpty { f.name }
            val size = humanBytes(f.length())
            val date = dateFmt.format(Date(f.lastModified()))
            append("$id  $size  $date  $rel\n")
        }
    }
}

/** Default instruction text for the apps AI flow. */
const val DEFAULT_APPS_AI_INSTRUCTION =
    "TidyTalk からの掃除相談です。以下のインストール済みアプリ一覧から、\n" +
        "アンインストールしても良さそうなもの（最終起動が古い、使っていない、重複、\n" +
        "容量が大きすぎる等）を判断してください。各行は\n" +
        "「ID  容量  最終起動日（n/a は過去1年未起動）  パッケージ名  表示名」です。\n" +
        "回答は、アンインストール推奨アプリの ID（行頭の英数字 6〜8 文字）を\n" +
        "カンマか改行区切りでコードブロック (```...```) に入れて返してください\n" +
        "（チャット UI のコピー機能がそのまま使えます）。理由のコメントは自由です。"

/** Locale-independent "YYYY-MM-DD" (user's local zone) or "n/a" rendering for [InstalledApp.lastUsedMillis]. */
fun formatLastUsed(millis: Long?): String =
    if (millis != null && millis > 0) {
        java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault()).toString()
    } else {
        "n/a"
    }

/**
 * Apps version of [buildAiPrompt]. Enumerates installed apps with stable IDs
 * that the AI echoes back inside a fenced code block.
 */
fun buildAppsAiPrompt(instruction: String, snapshot: List<PromptItem<InstalledApp>>): String =
    buildString {
        append(instruction.trimEnd())
        append("\n\n")
        append("対象: インストール済みアプリ ${snapshot.size} 件\n")
        append("--\n")
        snapshot.forEach { (id, app) ->
            val size = humanBytes(app.sizeBytes)
            val lastUsed = formatLastUsed(app.lastUsedMillis)
            append("$id  $size  $lastUsed  ${app.packageName}  ${app.label}\n")
        }
    }

sealed interface AnswerParseResult {
    data object NoIds : AnswerParseResult
    data class Ok(val validIds: List<String>, val invalidIds: List<String>) : AnswerParseResult
}

private val codeBlockRegex = Regex("""```[\s\S]*?```""")

/**
 * Matches base62 tokens of length 6–8 not adjacent to other base62 chars, which
 * covers both default-width and collision-widened IDs without false positives
 * on longer alphanumeric runs.
 */
private val idTokenRegex = Regex("""(?<![0-9A-Za-z])[0-9A-Za-z]{6,8}(?![0-9A-Za-z])""")

/**
 * Pulls IDs out of [answer]. Prefers the first fenced code block (the format
 * the prompt asks the AI to use); falls back to scanning the whole text so
 * that pasting just the code-block contents — what most chat UIs' Copy
 * buttons return — also works. IDs not present in [knownIds] are reported as
 * invalid so the UI can show what was dropped.
 */
fun parseAnswerIds(answer: String, knownIds: Set<String>): AnswerParseResult {
    val source = codeBlockRegex.find(answer)?.value ?: answer
    val all = idTokenRegex.findAll(source).map { it.value }.distinct().toList()
    if (all.isEmpty()) return AnswerParseResult.NoIds
    val (valid, invalid) = all.partition { it in knownIds }
    return AnswerParseResult.Ok(valid, invalid)
}

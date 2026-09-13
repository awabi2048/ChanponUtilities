package me.awabi2048.chanponutilities.safety

/**
 * セーフガードが確認対象にするコマンドと、その中のエンティティ対象引数を認識します。
 *
 * BukkitのコマンドAPIには全コマンドの引数型を公開する共通インターフェースがないため、
 * 実行結果を推測するためにコマンド文字列全体を正規表現だけで走査しません。例えば
 * scoreboardのobjective名やtellraw本文に含まれた「@e」を対象セレクターと誤認しないよう、
 * 既知のコマンドごとに対象引数の位置を定義しています。
 */
internal data class ParsedSafeguardCommand(
    val normalizedCommand: String,
    val root: String,
    val selectors: List<String>,
)

internal object CommandSafeguardCommandParser {
    private val selectorPattern = Regex("""@(?:p|a|r|e|s)(?:\[.*])?""", RegexOption.IGNORE_CASE)

    /**
     * 複数対象へ作用するVanillaコマンドの対象引数を定義します。
     * 範囲変更・全体変更のようにセレクターを持たないコマンドは、この判定とは別の
     * ルールが必要なため、ここでは意図的に含めません。
     */
    private val supportedRoots = setOf(
        "advancement",
        "clear",
        "effect",
        "enchant",
        "execute",
        "experience",
        "gamemode",
        "give",
        "item",
        "kill",
        "particle",
        "playsound",
        "recipe",
        "scoreboard",
        "spawnpoint",
        "spreadplayers",
        "stopsound",
        "tag",
        "team",
        "teleport",
        "tellraw",
        "title",
        "tp",
        "xp",
    )

    fun parse(message: String): ParsedSafeguardCommand? {
        val normalized = message.trim()
        if (!normalized.startsWith('/')) return null

        val tokens = tokenize(normalized.removePrefix("/").trim())
        val root = tokens.firstOrNull()?.let(::normalizeRoot) ?: return null
        if (root !in supportedRoots) return null

        val args = tokens.drop(1)
        val selectors = targetArgumentIndexes(root, args)
            .asSequence()
            .filter { it in args.indices }
            .map { args[it] }
            .filter(::isSelector)
            .toList()
        if (selectors.isEmpty()) return null

        return ParsedSafeguardCommand(normalized, root, selectors)
    }

    private fun normalizeRoot(token: String): String? {
        val normalized = token.lowercase()
        val namespaceSeparator = normalized.indexOf(':')
        if (namespaceSeparator < 0) return normalized

        // Vanillaの名前空間付き表記（minecraft:kill等）だけを同一コマンド類とします。
        // 他プラグインが同じ末尾名を持つ独自コマンドを誤って捕捉しないためです。
        if (normalized.substring(0, namespaceSeparator) != "minecraft") return null
        return normalized.substring(namespaceSeparator + 1).ifBlank { null }
    }

    private fun isSelector(token: String): Boolean = selectorPattern.matches(token)

    /** argsはルートコマンドを除いた引数列です。 */
    private fun targetArgumentIndexes(root: String, args: List<String>): Set<Int> = when (root) {
        "advancement", "experience", "recipe", "xp" ->
            if (args.firstOrNull()?.lowercase() in setOf("grant", "revoke", "give", "take", "add", "set", "query")) {
                setOf(1)
            } else {
                emptySet()
            }

        "clear", "enchant", "give", "kill", "spawnpoint", "stopsound", "tag", "tellraw", "title" -> setOf(0)
        "effect" -> if (args.firstOrNull()?.lowercase() in setOf("give", "clear")) setOf(1) else emptySet()
        "gamemode" -> setOf(1)
        "item" -> {
            val entityKeyword = args.indexOfFirst { it.equals("entity", ignoreCase = true) }
            if (entityKeyword >= 0) setOf(entityKeyword + 1) else emptySet()
        }
        "particle" -> args.indices.lastOrNull()?.let(::setOf) ?: emptySet()
        "playsound" -> setOf(2)
        "spreadplayers" -> args.indices.lastOrNull()?.let(::setOf) ?: emptySet()
        "team" -> when (args.firstOrNull()?.lowercase()) {
            "join" -> setOf(2)
            "leave" -> setOf(1)
            else -> emptySet()
        }
        "scoreboard" -> scoreboardTargetArgumentIndexes(args)
        "teleport", "tp" -> setOf(0)
        // executeは実行者選択、条件判定、内部コマンドのいずれにもセレクターが
        // 現れるため、見つかった全セレクターを保守的に確認対象へ含めます。
        "execute" -> args.indices.toSet()
        else -> emptySet()
    }

    private fun scoreboardTargetArgumentIndexes(args: List<String>): Set<Int> {
        if (args.firstOrNull()?.lowercase() != "players") return emptySet()
        return when (args.getOrNull(1)?.lowercase()) {
            "set", "add", "remove", "reset", "enable", "random" -> setOf(2)
            "operation" -> setOf(2, 4)
            else -> emptySet()
        }
    }

    /**
     * 空白を区切りとして使いつつ、セレクターの[]、NBTの{}、引用符内の空白を保持します。
     * 単純なsplit(" ")では、nbt条件を含む有効なセレクターを分断してしまいます。
     */
    private fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var squareDepth = 0
        var curlyDepth = 0
        var parenthesisDepth = 0
        var quoted = false
        var escaped = false

        fun flush() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }

        for (character in command) {
            if (escaped) {
                current.append(character)
                escaped = false
                continue
            }
            if (quoted && character == '\\') {
                current.append(character)
                escaped = true
                continue
            }
            if (character == '"') {
                quoted = !quoted
                current.append(character)
                continue
            }
            if (!quoted) {
                when (character) {
                    '[' -> squareDepth++
                    ']' -> if (squareDepth > 0) squareDepth--
                    '{' -> curlyDepth++
                    '}' -> if (curlyDepth > 0) curlyDepth--
                    '(' -> parenthesisDepth++
                    ')' -> if (parenthesisDepth > 0) parenthesisDepth--
                }
            }

            if (
                character.isWhitespace() &&
                !quoted &&
                squareDepth == 0 &&
                curlyDepth == 0 &&
                parenthesisDepth == 0
            ) {
                flush()
            } else {
                current.append(character)
            }
        }
        flush()
        return tokens
    }
}

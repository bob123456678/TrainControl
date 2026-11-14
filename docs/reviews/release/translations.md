# Translation review of TrainControl {RELEASE}: every string added or changed since {LAST_TAG}

Model: Sonnet. Read-only. Prefix `{PREFIX}`.

## Rules

- **Repository:** C:/Users/adamo/OneDrive/Documents/NetBeansProjects/TrainControl, a Java 8 Swing app that drives a Marklin model railway through a Central Station 2/3.
- **Read** with `git show {DEV}:<path>` and `git diff {LAST_TAG} {DEV} -- <path>`. Don't rely on the main checkout's working files. Don't commit, merge or push; change no file in the repository.
- **Read first:** `CLAUDE.md`, the orchestrator's `builder-rules.md`.
- **Never** read or write `cs2_sample_layout/`, LocDB.data, UIState.data or preferences. No window tests. Scripts for counting and comparing go in your own scratch folder only.
- When the orchestrator says a calibration run is on, don't read tracker items filed or commented on after `{FREEZE}`.

## Method

1. **List the keys:** `git diff {LAST_TAG} {DEV} -- src/org/traincontrol/resources/messages.properties` gives the English keys added or changed. Count new and changed.
2. **Check every key in all 7 other bundles** (`messages_de`, `_fr`, `_nl`, `_es`, `_it`, `_pl`, `_da`), by script for the mechanical points and by reading for meaning:
   - present, and not left in English (unless the term is the same in that language: QWERTY, Fn, Multi-Unit and so on);
   - the same meaning as the English;
   - the same placeholders (`{0}`, `{1}`, `%s`) and `\n` counts, in a sensible order;
   - the same HTML tags;
   - ASCII with `\uXXXX` escapes only, none malformed;
   - no raw apostrophe in a `MessageFormat` string;
   - German uses "Fahrstra\u00dfe" (escaped) for the Routes tab and its import, export and delete texts, and "Route" for autonomy routes; check the existing German bundle's usage if unsure;
   - no text likely to overflow: flag a button, menu or column label over about 1.5 times its English length, and say where it is shown (a fixed-width column is worth a look; a sentence is not);
   - register and terms match the same language's older keys (formal or informal "you" in es and nl, and so on).
3. **Changed English keys:** if the English change was only spelling (UK to US), the translations are not stale. If the meaning changed, the translation must change.
4. **Meaning:** read at least a third of the new keys in every language, newest features first, and every key that carries a warning or a confirmation. Last time the mechanical pass was clean and only the reading found wording defects.

## Grades and report

- **B:** a missing key, a placeholder or tag mismatch that can throw or garble a message, or a mistranslation that reverses a warning.
- **C:** tone, terminology, length, a weaker sentence.
- **D:** a checked clean group (presence, ASCII, placeholders, tags, German terms).

Report to the file the orchestrator names: a title; `**Status:** open`; `**Prefix:** {PREFIX}`; `**Reviewed:** dev {DEV} against {LAST_TAG}, <date>`; a method paragraph with the counts (keys, new, changed; how many read by eye per language). Then one table with columns key, language, problem, suggested text (the first cell is a key, never id-like). Then D headings for each clean mechanical check, and a `### {PREFIX}-B1` or `-C1` heading with a `| **Disposition** | open |` table for anything that needs a decision rather than a one-line text swap.

End with the report line: counts by grade; each B in one line; what wasn't reached (languages or keys checked only mechanically); `Converged: yes` or `Converged: no`.

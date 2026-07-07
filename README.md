# MacPad++

A Notepad++-style text editor for macOS.

## Features

- Multi-tab editing with session restore, including unsaved buffers
- Syntax highlighting for ~45 languages, auto-detected and switchable
- Advanced Find/Replace: regex, match case, whole word, extended mode (`\n`, `\t`), wrap-around, direction, Mark All, Count, and Find in Files
- Line operations: duplicate, delete, move up/down, join, sort ascending/descending, remove duplicates, trim trailing whitespace
- Clone a document into a second, side-by-side view (shared text, synced live), or move a tab between views
- Line bookmarks with toggle and jump-to-next/previous
- Encoding detection and conversion: UTF-8 (±BOM), UTF-16 LE/BE, Latin-1, Windows-1252
- Line-ending conversion: LF / CRLF / CR
- Convert case and indentation (tabs ↔ spaces), toggle comments, go to matching bracket
- Open from Finder via double-click or Open With; reveal in Finder; copy full path; rename or move a file to Trash
- Native screen menu bar, dark/light themes, whitespace / EOL / indent-guide display, code folding, zoom, word wrap, print, drag & drop

## Install

Requires macOS (Apple Silicon or Intel).

1. Download the latest **MacPad++.dmg** from the [Releases page](https://github.com/srujanakalluru/MacPadPP/releases/latest).
2. Open the DMG and drag **MacPad++.app** into Applications.
3. Launch it once so macOS registers it - MacPad++ then appears under Finder's right-click **Open With**. If macOS blocks the first launch, right-click **MacPad++.app** and choose **Open**.

## Build from source

Requires JDK 22 or newer and Maven.

```
bash build-dmg.sh
```

This produces a self-contained `target/jp/out/MacPad++-1.0.0.dmg` with a bundled Java runtime.

## License

Proprietary - all rights reserved. See [LICENSE](LICENSE). The source is published for viewing only and may not be used, copied, modified, or redistributed without written permission. Bundled dependencies keep their own licenses (RSyntaxTextArea BSD-3-Clause, FlatLaf Apache-2.0, Lombok MIT); their full texts are in [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt).

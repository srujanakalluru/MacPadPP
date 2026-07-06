# MacPad++ - working guidelines

## Code comments
Write only comments that carry non-trivial information: a subtle invariant, a
non-obvious reason for doing something an unusual way, or a warning about a
gotcha. Never write educative or narrating comments that restate what the code
already says (e.g. "// loop over the buffers", "// set the title", "// Entry 0 -
editor for all text files"). If a competent reader would understand the line
without the comment, omit it. Default to no comment.

## README and docs
The README and any shipped docs are written for the people who will download the
app or fork the repo - never for us, the developers. Include only: what the app
is, what it does, and how to install/build/run it.

Never add developer-facing content: the rationale behind a technology, library,
or version choice; problems we ran into and how we worked around them; "do X
because otherwise Y happened" notes; build-environment caveats; project history;
or progress narrative. When we fix a bug or hit a setup snag, fix it in the code
or the build script - do not document our troubleshooting in the README. State
requirements bare ("JDK 22 or newer"), never why they exist.

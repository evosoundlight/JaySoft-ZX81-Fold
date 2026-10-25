# ZX81 Fold 1.0 RC1 — Copyright, licences, and third-party credits

JaySoft ZX81 Fold is an independent, unofficial Android ZX81 emulator project.
It is not affiliated with or endorsed by Sinclair, Nine Tiles or the ZX84 author.

## 1. JaySoft-authored additions (GPL-2.0-or-later)

Copyright (C) 2026 JaySoft project contributors, for contributions they own.

JaySoft-authored application code, interface code, and Android integration are
made available under the GNU General Public License, version 2 or (at your
option) any later version. You may redistribute and/or modify those
contributions under those terms. A copy of GPL version 2 is in `COPYING`.

The relevant source files include:
- `app/src/main/java/com/zx81fold/emulator/MainActivity.java` (JaySoft-specific integration)
- `app/src/main/assets/index.html` (JaySoft-authored app/UI elements)

The above grant applies ONLY to rights that JaySoft contributors actually hold.
The HTML also incorporates the ZX84 hardware/emulation core, which retains the
original author's attribution and MIT licence. The source file's historical
embedded MIT notice must not be removed.

This software is provided WITHOUT ANY WARRANTY; see GPL version 2.

## 2. ZX84 hardware and CPU emulator (MIT)

Original ZX84 author: Damien Guard, Copyright (c) 2025–2026.
The MIT licence is preserved in `ZX84-LICENSE.txt` and in the HTML header.
MIT-covered components are not represented as original JaySoft inventions.

## 3. Sinclair ZX81 improved BASIC ROM (GPL-2.0-or-later)

ROM copyright: Nine Tiles Networks Ltd (1981), as identified in the
Open81 assembly source. `ROM-SOURCE/open81.asm` contains the source's grant
of GNU GPL v2 or any later version. Its historical provenance and assembly
instructions are included in `ROM-SOURCE/`.

The included 8192-byte ROM matches the binary bundled inside the application.
ROM SHA-256:
14ad84f4243efcd41587ff46ab932d11087043e8d455a1ed2a227b9657828dfa

The current audit did not independently rerun the Pasmo assembler, but the
historical release attributes this assembly to the improved ROM source.

## 4. Android tools and bundled third-party dependencies

The build uses the Android SDK, Android Gradle Plugin, and GitHub Actions;
these build tools are not relicensed by JaySoft. Android system components
remain subject to their own licences. Additional third-party assets, if any,
must be identified and cleared prior to wide public distribution.

## Source and build

Exact source snapshot: `ZX81-Fold-rc1-prep.zip` downloaded on 25 Oct 2026.
Signed build workflow: https://github.com/evosoundlight/ZX81-Fold/actions/runs/40206874153
Run commit indicated by build cache key:
8a5ffea661bacd6850be35d8f842d303abd9e89c

The source ZIP reflects the branch snapshot and accompanying licence/source
materials. Its equivalence to the immutable commit has not been independently
confirmed via a GitHub API checkout; verify before publishing a release.
The signing key and passwords are *not* provided in source distributions.

# Third-party notices

This project is licensed under the GNU General Public License v3.0 or later
(see `LICENSE`). The release JAR also contains the following third-party
components under their own licenses, all of which are GPL-3.0 compatible.

## legacy-lwjgl3

`libs/legacy-lwjgl3.jar` is the JitPack build of
[Zarzelcow/legacy-lwjgl3](https://github.com/Zarzelcow/legacy-lwjgl3), commit
`78643b2a9621ab04d13e3ec0a222874d793516d2`. It is relocated to `org.lwjglx`
at build time and shipped inside the release JAR. It is distributed under the
GNU LGPL v2.1 or later; the license text is included in the archive as
`LICENSE_legacy-lwjgl3`.

## MC-LWJGL3 compatibility API

The sources under `src/main/java/org/lwjglx/` (the `openal` package, `Sys`,
`LWJGLUtil` and `LWJGLException`) are derived from
[gudenau/MC-LWJGL3](https://github.com/gudenau/MC-LWJGL3), commit
`026bb8683732ad1b7362e65b252e5d90e309613f`, licensed under the GNU LGPL v3
(`licenses/LGPL-3.0.txt`).

## LWJGL 2

`org.lwjglx.BufferUtils` and parts of the API surface reproduced by the
compatibility layer originate from LWJGL 2, Copyright (c) 2002-2008
Lightweight Java Game Library Project, licensed under the BSD 3-Clause
license (`licenses/BSD-3-Clause-LWJGL.txt`).

## LWJGL 3

LWJGL 3.3.3 Java bindings and platform natives (GLFW, OpenAL Soft, and
others) are obtained from Maven Central (`org.lwjgl`) and licensed under the
BSD 3-Clause license. The upstream JARs carry no license file, so the text is
provided in `licenses/BSD-3-Clause-LWJGL.txt`.

The bundled natives include libraries under their own licenses:

- GLFW: zlib/libpng license (`licenses/Zlib-GLFW.txt`).
- OpenAL Soft: GNU LGPL v2 or later (`licenses/LGPL-2.0-OpenAL-Soft.txt`).
- NanoVG: zlib license.
- stb: public domain or MIT, at the user's choice.

## Kotlin standard library

`org.jetbrains.kotlin:kotlin-stdlib` 1.6.10 (required by the legacy-lwjgl3
`Display` implementation) is bundled under the Apache License 2.0
(`licenses/Apache-2.0.txt`).

The release JAR carries `LICENSE`, this file and the `licenses/` directory
under `META-INF/`.

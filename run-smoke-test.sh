#!/usr/bin/env bash
# Launches an existing Forge 1.8.9 instance directly (no launcher) for smoke
# testing. The instance must already have this mod in its mods directory.
#
#   MC_ROOT=/path/to/.minecraft MC_VERSION=<version dir name> \
#   JAVA8=/path/to/java8/bin/java ./run-smoke-test.sh
#
# LWJGL3_INPUT_DIAGNOSTICS=true enables the [LLLLLwjgl3/input] log lines.
set -euo pipefail

: "${MC_ROOT:?set MC_ROOT to the .minecraft directory}"
: "${MC_VERSION:?set MC_VERSION to the Forge 1.8.9 version directory name}"
VERSION="$MC_VERSION"
VERSION_DIR="$MC_ROOT/versions/$VERSION"
JAVA="${JAVA8:-java}"
USERNAME="${MC_USERNAME:-Player}"

if ! command -v "$JAVA" >/dev/null 2>&1; then
    echo "Java 8 not found: $JAVA (set JAVA8)" >&2
    exit 1
fi

# Classpath order of a vanilla Forge 1.8.9-11.15.1.2318 launch.
LIBRARIES=(
    "$MC_ROOT/libraries/com/mojang/netty/1.8.8/netty-1.8.8.jar"
    "$MC_ROOT/libraries/oshi-project/oshi-core/1.1/oshi-core-1.1.jar"
    "$MC_ROOT/libraries/net/java/dev/jna/jna/3.4.0/jna-3.4.0.jar"
    "$MC_ROOT/libraries/net/java/dev/jna/platform/3.4.0/platform-3.4.0.jar"
    "$MC_ROOT/libraries/com/ibm/icu/icu4j-core-mojang/51.2/icu4j-core-mojang-51.2.jar"
    "$MC_ROOT/libraries/net/sf/jopt-simple/jopt-simple/4.6/jopt-simple-4.6.jar"
    "$MC_ROOT/libraries/com/paulscode/codecjorbis/20101023/codecjorbis-20101023.jar"
    "$MC_ROOT/libraries/com/paulscode/codecwav/20101023/codecwav-20101023.jar"
    "$MC_ROOT/libraries/com/paulscode/libraryjavasound/20101123/libraryjavasound-20101123.jar"
    "$MC_ROOT/libraries/com/paulscode/librarylwjglopenal/20100824/librarylwjglopenal-20100824.jar"
    "$MC_ROOT/libraries/com/paulscode/soundsystem/20120107/soundsystem-20120107.jar"
    "$MC_ROOT/libraries/io/netty/netty-all/4.0.23.Final/netty-all-4.0.23.Final.jar"
    "$MC_ROOT/libraries/com/google/guava/guava/17.0/guava-17.0.jar"
    "$MC_ROOT/libraries/org/apache/commons/commons-lang3/3.3.2/commons-lang3-3.3.2.jar"
    "$MC_ROOT/libraries/commons-io/commons-io/2.4/commons-io-2.4.jar"
    "$MC_ROOT/libraries/commons-codec/commons-codec/1.9/commons-codec-1.9.jar"
    "$MC_ROOT/libraries/net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar"
    "$MC_ROOT/libraries/net/java/jutils/jutils/1.0.0/jutils-1.0.0.jar"
    "$MC_ROOT/libraries/com/google/code/gson/gson/2.2.4/gson-2.2.4.jar"
    "$MC_ROOT/libraries/com/mojang/authlib/1.5.21/authlib-1.5.21.jar"
    "$MC_ROOT/libraries/com/mojang/realms/1.7.59/realms-1.7.59.jar"
    "$MC_ROOT/libraries/org/apache/commons/commons-compress/1.8.1/commons-compress-1.8.1.jar"
    "$MC_ROOT/libraries/org/apache/httpcomponents/httpclient/4.3.3/httpclient-4.3.3.jar"
    "$MC_ROOT/libraries/commons-logging/commons-logging/1.1.3/commons-logging-1.1.3.jar"
    "$MC_ROOT/libraries/org/apache/httpcomponents/httpcore/4.3.2/httpcore-4.3.2.jar"
    "$MC_ROOT/libraries/org/apache/logging/log4j/log4j-api/2.0-beta9/log4j-api-2.0-beta9.jar"
    "$MC_ROOT/libraries/org/apache/logging/log4j/log4j-core/2.0-beta9/log4j-core-2.0-beta9.jar"
    "$MC_ROOT/libraries/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"
    "$MC_ROOT/libraries/org/lwjgl/lwjgl/lwjgl_util/2.9.4-nightly-20150209/lwjgl_util-2.9.4-nightly-20150209.jar"
    "$MC_ROOT/libraries/tv/twitch/twitch/6.5/twitch-6.5.jar"
    "$MC_ROOT/libraries/net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9.jar"
    "$MC_ROOT/libraries/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar"
    "$MC_ROOT/libraries/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar"
    "$MC_ROOT/libraries/jline/jline/2.13/jline-2.13.jar"
    "$MC_ROOT/libraries/com/typesafe/akka/akka-actor_2.11/2.3.3/akka-actor_2.11-2.3.3.jar"
    "$MC_ROOT/libraries/com/typesafe/config/1.2.1/config-1.2.1.jar"
    "$MC_ROOT/libraries/org/scala-lang/scala-actors-migration_2.11/1.1.0/scala-actors-migration_2.11-1.1.0.jar"
    "$MC_ROOT/libraries/org/scala-lang/scala-compiler/2.11.1/scala-compiler-2.11.1.jar"
    "$MC_ROOT/libraries/org/scala-lang/plugins/scala-continuations-library_2.11/1.0.2/scala-continuations-library_2.11-1.0.2.jar"
    "$MC_ROOT/libraries/org/scala-lang/plugins/scala-continuations-plugin_2.11.1/1.0.2/scala-continuations-plugin_2.11.1-1.0.2.jar"
    "$MC_ROOT/libraries/org/scala-lang/scala-library/2.11.1/scala-library-2.11.1.jar"
    "$MC_ROOT/libraries/org/scala-lang/scala-parser-combinators_2.11/1.0.1/scala-parser-combinators_2.11-1.0.1.jar"
    "$MC_ROOT/libraries/org/scala-lang/scala-reflect/2.11.1/scala-reflect-2.11.1.jar"
    "$MC_ROOT/libraries/org/scala-lang/scala-swing_2.11/1.0.1/scala-swing_2.11-1.0.1.jar"
    "$MC_ROOT/libraries/org/scala-lang/scala-xml_2.11/1.0.2/scala-xml_2.11-1.0.2.jar"
    "$MC_ROOT/libraries/lzma/lzma/0.0.1/lzma-0.0.1.jar"
    "$MC_ROOT/libraries/java3d/vecmath/1.5.2/vecmath-1.5.2.jar"
    "$MC_ROOT/libraries/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar"
    "$VERSION_DIR/$VERSION.jar"
)
CP=''
for library in "${LIBRARIES[@]}"; do
    [[ -f "$library" ]] || { echo "Missing dependency: $library" >&2; exit 1; }
    CP+="${CP:+:}$library"
done
cd "$VERSION_DIR"

exec "$JAVA" \
    -Xmx"${MC_XMX:-4G}" \
    -Dfile.encoding=UTF-8 \
    -Dlllllwjgl3.inputDiagnostics="${LWJGL3_INPUT_DIAGNOSTICS:-false}" \
    -Dsun.stdout.encoding=UTF-8 \
    -Dsun.stderr.encoding=UTF-8 \
    -Djava.rmi.server.useCodebaseOnly=true \
    -Dcom.sun.jndi.rmi.object.trustURLCodebase=false \
    -Dcom.sun.jndi.cosnaming.object.trustURLCodebase=false \
    -Dlog4j2.formatMsgNoLookups=true \
    -Dminecraft.client.jar="$VERSION_DIR/$VERSION.jar" \
    -Djava.net.useSystemProxies=true \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UnlockDiagnosticVMOptions \
    -XX:+UseG1GC \
    -XX:G1MixedGCCountTarget=5 \
    -XX:G1NewSizePercent=20 \
    -XX:G1ReservePercent=20 \
    -XX:MaxGCPauseMillis=50 \
    -XX:G1HeapRegionSize=32m \
    -XX:-OmitStackTraceInFastThrow \
    -XX:MaxInlineLevel=15 \
    -XX:-DontCompileHugeMethods \
    -XX:MaxNodeLimit=240000 \
    -XX:NodeLimitFudgeFactor=8000 \
    -XX:TieredCompileTaskTimeout=10000 \
    -XX:ReservedCodeCacheSize=400M \
    -XX:NmethodSweepActivity=1 \
    -Dfml.ignoreInvalidMinecraftCertificates=true \
    -Dfml.ignorePatchDiscrepancies=true \
    -Djava.library.path="$VERSION_DIR/natives-linux-x86_64" \
    -cp "$CP" \
    net.minecraft.launchwrapper.Launch \
    --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker \
    --username "$USERNAME" \
    --version "$VERSION" \
    --gameDir "$VERSION_DIR" \
    --assetsDir "$MC_ROOT/assets" \
    --assetIndex 1.8 \
    --uuid 00000000000000000000000000000000 \
    --accessToken 0 \
    --userProperties '{}' \
    --userType legacy \
    --width 854 \
    --height 480

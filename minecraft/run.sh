#!/usr/bin/env bash
cd "$(dirname "$0")"
[ -f build/Minecraft.jar ] || ./build.sh
exec ${JAVA:-java} -Xms256m -Xmx2g -Dmc.data=data -jar build/Minecraft.jar "$@"

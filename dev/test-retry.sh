#!/usr/bin/env bash

echo Starting tests ...
retry_attempts=5

for ((i=1; i <= $retry_attempts; i++)); do
	if gradle jvmTest jsTest wasmJsTest -P'org.gradle.jvmargs=-Xmx512Mi -XX:MaxPermSize=512Mi' --max-workers=2; then
		exit 0
	fi
done

exit 1
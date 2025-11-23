{ ... }:

{
  perSystem = { system, config, pkgs, lib, common, ... }: {
    devShells.ci-test = pkgs.mkMinimalShell {
      nativeBuildInputs = common.packages;
      shellHook = ''
        retry_attempts=3
        for i in {1..$retry_attempts}; do
          if gradle spotlessCheck jvmTest jsTest wasmJsTest -P'org.gradle.jvmargs=-Xmx512Mi -XX:MaxPermSize=512Mi' --max-workers=2; then
            exit 0
          fi
        done
      '';
    };
  };
}

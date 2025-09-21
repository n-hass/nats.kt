{ ... }:

{
  perSystem = { system, config, pkgs, lib, ... }: {
    miniShell = {
      packages = with pkgs; [
        jdk21
        gradle

        nats-server
        natscli
      ];

      env = {
        CHROME_BIN = {
          "aarch64-darwin" = "${lib.getExe pkgs.google-chrome}";
          "x86_64-darwin" = "${lib.getExe pkgs.google-chrome}";
          "x86_64-linux" = "${lib.getExe pkgs.chromium}";
        }."${system}" or "${lib.getExe pkgs.brave}";
      };

      returnToUserShell = true;

      shellHook = ''
        ${config.pre-commit.installationScript}
      '';
    };
  };
}

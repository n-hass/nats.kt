{ ... }:

{
  perSystem = { system, config, pkgs, lib, common, ... }: {

    _module.args.common = {
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
    };

    miniShell = {
      packages = common.packages;
      env = common.env;

      returnToUserShell = true;

      shellHook = ''
        ${config.pre-commit.installationScript}
      '';
    };

    devShells.ci = pkgs.mkMinimalShell {
      nativeBuildInputs = common.packages;
    };
  };
}

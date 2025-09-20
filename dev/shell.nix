{ ... }:

{
  perSystem = { system, config, pkgs, ... }: {
    miniShell = {
      packages = with pkgs; [
        jdk21
        gradle

        nats-server
        natscli
      ];

      returnToUserShell = true;

      shellHook = ''
        ${config.pre-commit.installationScript}
      '';
    };
  };
}

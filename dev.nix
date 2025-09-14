{ ... }:

{
  perSystem = { system, pkgs, ... }: {
    miniShell = {
      packages = with pkgs; [
        jdk21
        gradle

        nats-server
        natscli
      ];
    };
  };
}

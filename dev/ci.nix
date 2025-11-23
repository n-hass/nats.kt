{ ... }:

{
  perSystem = { system, config, pkgs, lib, common, ... }: {
    devShells.ci = pkgs.mkMinimalShell {
      nativeBuildInputs = common.packages;
    };
  };
}

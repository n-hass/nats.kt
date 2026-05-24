{ ... }:

{
  perSystem = { system, config, pkgs, lib, common, ... }: {
    devShells.ci = pkgs.mkMinimalShell {
      nativeBuildInputs = map (p: if p == pkgs.jdk21 then pkgs.jdk21_headless else p) common.packages;
    };
  };
}

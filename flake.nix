{
  description = "NATS.kt development environment";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    mini-dev-shell = {
      url = "github:n-hass/mini-dev-shell";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    flake-parts.url = "github:hercules-ci/flake-parts";
    git-hooks-nix = {
      url = "github:cachix/git-hooks.nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };


  outputs = inputs@{ flake-parts, mini-dev-shell, git-hooks-nix, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      imports = [
        mini-dev-shell.flakeModule
        git-hooks-nix.flakeModule
        ./dev/ci.nix
        ./dev/hooks.nix
        ./dev/shell.nix
      ];

      perSystem = { system, lib, config, ... }: let
        pkgs = import inputs.nixpkgs {
          inherit system;
          config = {
            allowUnfreePredicate = pkg: builtins.elem (lib.getName pkg) [
              "google-chrome"
            ];
          };
          overlays = [
            mini-dev-shell.overlays.default
            mini-dev-shell.overlays.mk-minimal-shell
          ];
        };
      in {
        _module.args = {
            inherit pkgs;
        };
      };

      systems = [ "x86_64-linux" "aarch64-linux" "aarch64-darwin" "x86_64-darwin" ];
    };
}

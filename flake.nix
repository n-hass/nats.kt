{
  description = "NATS.kt development environment";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    mini-dev-shell = {
      url = "github:n-hass/mini-dev-shell";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    flake-parts.url = "github:hercules-ci/flake-parts";
  };


  outputs = inputs@{ flake-parts, mini-dev-shell, ... }: 
    flake-parts.lib.mkFlake { inherit inputs; } {
      imports = [
        mini-dev-shell.flakeModule
        ./dev.nix
      ];

      systems = [ "x86_64-linux" "aarch64-linux" "aarch64-darwin" "x86_64-darwin" ];
    };
}

{ ... }:

{
  perSystem = { config, system, pkgs, ... }: {
    pre-commit.settings.hooks.spotless = let
      script = pkgs.writeShellScript "spotlessApply" ''
set -eo pipefail
REPO_DIR=$(git rev-parse --show-toplevel)
GIT_DIR=$(git rev-parse --absolute-git-dir)
# Store the output of 'git status' before running spotlessApply
BEFORE_SPOTLESS=$(git status --porcelain)

TASKS="spotlessApply"  # Root project

EXTRA_GRADLE_FLAGS=""

# pick Gradle vs wrapper
if command -v gradle >/dev/null 2>&1; then
    GRADLE_CMD="gradle $EXTRA_GRADLE_FLAGS $TASKS"
else
    GRADLE_CMD="$REPO_DIR/gradlew $EXTRA_GRADLE_FLAGS $TASKS"
fi

eval $GRADLE_CMD

# Store the output of 'git status' after running spotlessApply
AFTER_SPOTLESS=$(git status --porcelain)

# Compare BEFORE_SPOTLESS and AFTER_SPOTLESS
if [ "$BEFORE_SPOTLESS" != "$AFTER_SPOTLESS" ]; then
    printf "\e[31m---------------\nSpotless has modified some files. Aborting commit. Please review then re-commit.\n---------------\033[0m\n" >&2
    exit 1
fi
      '';
    in {
      enable = true;
      name = "spotless";
      entry = "${script}";
      language = "script";
      pass_filenames = false;
      stages = [ "pre-commit" ];
    };

    pre-commit.settings.hooks.conventional-commits = let
      script = pkgs.writeShellScript "" ''
#!/usr/bin/env bash

###
# Git Commit Message Hook - Conventional Commits + Ticket Number Injection
#
# This commit-msg hook enforces Conventional Commits formatting on commit messages
# and appends the associated ticket number into the commit body.
#
# Key Features:
# - Enforces commit message format: "<type>(<scope>): <message>"
#   Valid types include: build, docs, feat, fix, perf, refactor, style, test, chore.
# - Resolves ticket number in a prioritized manner:
#   1. Reads cached ticket from `.git/ticket-numbers/<branch>`.
#   2. Guesses ticket number from branch name (e.g., "PROJ-1234" from "feature/PROJ-1234").
#   3. Falls back to interactive prompt via `PromptInput.java` helper.
#      - If user cancels (exit code 233), branch is marked ignored.
# - Inserts the ticket number into the commit body as the first content line (e.g., "(#PROJ-1234)").
# - Provides helpful error messages and usage examples if commit message is malformed.
#
#
# Adapted from: https://github.com/tapsellorg/conventional-commits-git-hook
###

set -euo pipefail

types=('build' 'docs' 'feat' 'fix' 'perf' 'refactor' 'style' 'test' 'chore')

function build_regex() {
    regexp="^[.0-9]+$|"
    regexp="''${regexp}^([Rr]evert|[Mm]erge):? .*$|^(amend! |fixup! |squash! )?("

    for type in "''${types[@]}"; do
        regexp="''${regexp}$type|"
    done

    regexp="''${regexp%|})(\(.+\))(!)?: "
}

# Print out a standard error message if commit is malformed
function print_error() {
    echo -e "\n\e[31m[Invalid Commit Message]"
    echo -e "------------------------\033[0m\e[0m"
    echo -e "Valid types: \e[36m''${types[@]}\033[0m"
    echo -e "\e[37mActual commit message: \e[33m\"$msg_first_line\"\033[0m"
    echo -e "\e[37mExample valid commit message: \e[36m\"fix(module): message\"\033[0m"
    echo -e "\e[37mModule name (a.k.a. scope) is required\033[0m"
    echo -e "\e[37mRegex: \e[33m\"$regexp\"\033[0m"
}

# Main
INPUT_FILE=$1
build_regex
msg_first_line=$(head -n1 "$INPUT_FILE")
if ! [[ "$msg_first_line" =~ $regexp ]]; then
    print_error
    exit 2
fi
      '';
    in {
      enable = true;
      name = "conventional commits";
      entry = "${script}";
      language = "script";
      pass_filenames = true;
      stages = [ "commit-msg" ];
    };
  };
}
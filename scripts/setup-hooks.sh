#!/bin/bash

# Installs scripts/pre-commit into .git/hooks/pre-commit.

set -e

echo "Setting up Git hooks for the file manager API..."

PROJECT_ROOT="$(git rev-parse --show-toplevel)"
HOOKS_DIR="$PROJECT_ROOT/.git/hooks"
SCRIPTS_DIR="$PROJECT_ROOT/scripts"

if [ ! -d "$HOOKS_DIR" ]; then
    echo "Error: not in a Git repository"
    exit 1
fi

if [ ! -f "$SCRIPTS_DIR/pre-commit" ]; then
    echo "Error: pre-commit script not found at $SCRIPTS_DIR/pre-commit"
    exit 1
fi

echo "Installing pre-commit hook..."
cp "$SCRIPTS_DIR/pre-commit" "$HOOKS_DIR/pre-commit"
chmod +x "$HOOKS_DIR/pre-commit"

echo "Pre-commit hook installed."
echo ""
echo "On every commit the hook will:"
echo "  - format code with Spotless"
echo "  - compile main and test sources"
echo "  - verify formatting"
echo ""
echo "See scripts/README.md."

# Scripts

Utility scripts for the file manager API.

## Git hooks

### Pre-commit hook

The hook:

- formats Java with Spotless
- compiles main and test sources
- checks that formatting is still clean
- stops the commit if Spotless changed files, so you can review and stage them

### Setup

From Git Bash, or any shell that can run bash:

```bash
./scripts/setup-hooks.sh
```

On Windows PowerShell, call Git Bash. The `bash` command is a WSL stub and does not run the script:

```powershell
& "C:\Program Files\Git\bin\bash.exe" scripts/setup-hooks.sh
```

The script copies `scripts/pre-commit` to `.git/hooks/pre-commit` and marks it executable.

### Manual installation

```bash
cp scripts/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

### Verification

Change a Java file, stage it, and commit. The hook runs before the commit is created.

### Disabling the hook

```bash
mv .git/hooks/pre-commit .git/hooks/pre-commit.disabled
```

Turn it back on:

```bash
mv .git/hooks/pre-commit.disabled .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## Files

- `pre-commit` is the hook
- `setup-hooks.sh` installs it
- `README.md` is this file

## Troubleshooting

If the hook does not run, check that `.git/hooks/pre-commit` exists and is executable, and that `git status` works in this directory.

If the hook fails, run the printed Gradle command yourself. Spotless needs Java 21.

Skip the hook only when you mean to:

```bash
git commit --no-verify -m "Your commit message"
```

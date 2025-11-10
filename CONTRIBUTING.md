## 🤝 Contributing

We welcome contributions! NATS.kt is in active development and there are many opportunities to help:

1. **Check out our issues** - Look for "good first issue" labels
2. **Review the feature coverage** - Pick an unimplemented feature
3. **Improve documentation** - Help make NATS.kt more accessible
4. **Add examples** - Show how to use NATS.kt in real scenarios

### Development Workflow

1. Fork the repository
2. Use our Nix development environment (`nix develop` or direnv)
3. Make your changes following our code style
4. Add tests for new functionality
5. Ensure all tests pass (`gradle test`)
6. Submit a pull request

### Commit Convention

We use [Conventional Commits](https://www.conventionalcommits.org/). Examples:
- `feat(core): add request/reply support`
- `fix(transport): handle connection timeouts`
- `docs(readme): update installation instructions`

## 🛠️ Development Environment

We use **Nix** for a consistent, reproducible development environment.

### Using Nix

**Option 1: With direnv (Recommended)**
```bash
# Install direnv if you haven't already
# Then simply cd into the project directory
cd nats.kt
direnv allow
# direnv will automatically load the development environment
```

**Option 2: Manual Nix shell**
```bash
cd nats.kt
nix develop
```

### What's Included

Our Nix environment provides:
- **OpenJDK 21**
- **Gradle 8**
- **NATS Server**
- **NATS CLI**
- **Pre-commit hooks**

## 🔧 Building

```bash
# Build all targets
gradle build

# Run tests
gradle test

# Apply code formatting
gradle spotlessApply
```

## Publishing

- Must have the signing key, set up to use through GPG-Agent
- run `gradle publishToMavenCentral -Pnatskt.gpgsign=true -Pnatskt.version=x.y.z`
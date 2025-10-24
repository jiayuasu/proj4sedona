# GitHub Actions Workflows

This directory contains automated workflows for CI/CD, testing, and quality assurance.

## 📋 Workflows Overview

### 🧪 [CI (Continuous Integration)](ci.yml)

**Triggers:** Push to main branch, Pull Requests against main

**Jobs:**
- **Test**: Runs tests on Java 11, 17, and 21
  - Compiles all modules
  - Executes unit tests
  - Uploads test results as artifacts
  
- **Package**: Creates JAR artifacts
  - Builds all modules
  - Uploads core, wkt-parser, and mgrs JARs
  
- **Code Quality**: Enforces code standards
  - Checks Spotless formatting
  - Generates Javadoc
  
- **Benchmark**: Performance testing
  - Runs quick benchmarks (1000 iterations)
  - Compares with pyproj
  - Uploads benchmark reports
  
- **Multi-OS**: Cross-platform testing
  - Tests on Ubuntu, Windows, macOS
  
- **Coverage**: Code coverage reporting
  - Generates JaCoCo reports
  - Uploads to Codecov

**Artifacts:**
- Test results (XML/TXT)
- JAR files
- Benchmark reports

---

### ✅ [PR Checks](pr-checks.yml)

**Triggers:** Pull Request events (opened, synchronized, reopened, ready for review)

**Jobs:**
- **Validate**: Comprehensive PR validation
  - Checks for merge conflicts
  - Validates Maven POMs
  - Runs full test suite
  - Verifies code formatting
  
- **Size Check**: PR size analysis
  - Warns if PR is too large (>1000 lines)
  
- **Security Scan**: Dependency vulnerability check
  - Checks for outdated dependencies
  - Reports security vulnerabilities
  
- **Compatibility**: Backward compatibility check
  - Validates API compatibility
  
- **Comment Summary**: Posts test summary to PR
  - Adds automated comment with results

**Features:**
- Only runs on non-draft PRs
- Provides immediate feedback
- Helps maintain code quality

---

### 🔒 [CodeQL Analysis](codeql.yml)

**Triggers:**
- Push to main branches
- Pull Requests
- Weekly schedule (Monday 00:00 UTC)

**Jobs:**
- **Analyze**: Security and quality analysis
  - Scans for security vulnerabilities
  - Identifies code quality issues
  - Reports to GitHub Security tab

**Features:**
- Automated security scanning
- Continuous monitoring
- Integration with GitHub Advanced Security

---

### 📦 [Dependency Updates](dependency-update.yml)

**Triggers:**
- Weekly schedule (Monday 09:00 UTC)
- Manual workflow dispatch

**Jobs:**
- **Check Updates**: Dependency monitoring
  - Checks for Maven dependency updates
  - Checks for plugin updates
  - Creates dependency report
  - Opens GitHub issue if updates available

**Features:**
- Automated update notifications
- Weekly monitoring
- Detailed update reports

---

## 🔧 Configuration

### Required Secrets

Most workflows use default `GITHUB_TOKEN` permissions. For advanced features:

- **CODECOV_TOKEN** (optional): For Codecov integration
  - Add in: Settings → Secrets → Actions
  

### Permissions

Workflows use minimal required permissions:

- `contents: read` - Read repository content
- `security-events: write` - Write security alerts (codeql.yml)

### Caching

Maven dependencies are cached using `actions/cache`:
- Cache key: `${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}`
- Significantly speeds up builds

## 📊 Status Badges

Add these badges to your README:

```markdown
[![CI](https://github.com/YOUR_ORG/proj4sedona/workflows/CI/badge.svg)](https://github.com/YOUR_ORG/proj4sedona/actions/workflows/ci.yml)
[![CodeQL](https://github.com/YOUR_ORG/proj4sedona/workflows/CodeQL%20Analysis/badge.svg)](https://github.com/YOUR_ORG/proj4sedona/actions/workflows/codeql.yml)
[![codecov](https://codecov.io/gh/YOUR_ORG/proj4sedona/branch/main/graph/badge.svg)](https://codecov.io/gh/YOUR_ORG/proj4sedona)
```

## 🛠️ Local Testing

Test workflows locally using [act](https://github.com/nektos/act):

```bash
# Install act
brew install act  # macOS
# or
curl https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash

# Run CI workflow
act push -W .github/workflows/ci.yml

# Run PR checks
act pull_request -W .github/workflows/pr-checks.yml

# List all workflows
act -l
```

## 📝 Customization

### Modify Test Matrix

Edit `ci.yml` to test on different Java versions:

```yaml
strategy:
  matrix:
    java: ['11', '17', '21', '22']  # Add Java 22
```

### Adjust Benchmark Iterations

Edit `ci.yml` benchmark job:

```yaml
- name: Run benchmarks (quick)
  run: uv run python run_benchmarks.py --iterations 5000  # Increase iterations
```

## 🐛 Troubleshooting

### Build Failures

1. Check Java version compatibility
2. Clear Maven cache: Delete `.m2/repository`
3. Run locally: `mvn clean test -B`

### CodeQL Errors

- Ensure code compiles successfully
- Check for Java syntax errors
- Review CodeQL query packs

### Benchmark Failures

- Ensure Python dependencies are installed
- Check that JAR files are built
- Verify `uv` installation


## 📚 Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Maven CI/CD Best Practices](https://maven.apache.org/guides/mini/guide-ci-friendly-versions.html)
- [CodeQL Documentation](https://codeql.github.com/docs/)
- [Codecov Documentation](https://docs.codecov.io/)

## 🤝 Contributing

When adding new workflows:

1. Test locally with `act` if possible
2. Use descriptive job and step names
3. Add comments for complex logic
4. Update this README
5. Request review from maintainers


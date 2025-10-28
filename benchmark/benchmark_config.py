"""
Benchmark configuration settings.
Centralized version management using Maven.
"""

import subprocess
from pathlib import Path


def get_project_version():
    """
    Read the project version from pom.xml using Maven.
    Returns: Version string (e.g., "1.0.0-SNAPSHOT")
    """
    benchmark_dir = Path(__file__).parent
    parent_dir = benchmark_dir.parent
    
    try:
        # Use Maven to get the project version
        result = subprocess.run(
            ['mvn', 'help:evaluate', '-Dexpression=project.version', '-q', '-DforceStdout'],
            cwd=parent_dir,
            capture_output=True,
            text=True,
            timeout=10
        )
        
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip()
        
    except Exception:
        pass
    
    # Fallback to default version
    return "1.0.0-SNAPSHOT"


# Get version once at module load time
PROJECT_VERSION = get_project_version()

